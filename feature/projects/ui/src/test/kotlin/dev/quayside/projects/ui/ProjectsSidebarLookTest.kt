package dev.quayside.projects.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.quayside.core.designsystem.AmFonts
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Look of the sidebar (AM-816): count chips, the selected row, the section label, "Dodaj projekt z dysku…". */
@OptIn(ExperimentalTestApi::class)
internal class ProjectsSidebarLookTest {
    private val busy = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Busy")
    private val quiet = aProject(id = "project0002", dir = Path.of("/Users/test/projects/quiet"), displayName = "Quiet")
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.panel.argb()

    private fun world(vararg sessions: WorldSession) =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(busy, quiet)), running = sessions.toList())

    private fun busySessions() =
        (1..2).map { WorldSession(aSessionId("w$it"), A_PROJECT_DIR, SessionState.AwaitingInput(null)) } +
            (1..3).map { WorldSession(aSessionId("p$it"), A_PROJECT_DIR, SessionState.Working) }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    @Test
    fun `project row shows count chips as pills with descriptions`() =
        runComposeUiTest {
            // Given a project with 2 waiting and 3 working sessions, drawn at 4 px per dp
            showProjects(world(*busySessions().toTypedArray())) { Scaled4 { ProjectsSidebar(it) } }
            val row = ProjectsTags.row(busy.id)
            awaitTag(row)
            val waiting = hasContentDescription(ProjectsStrings.format(UiText("row.waiting", listOf("2"))))
            waitUntil { onAllNodes(waiting and inTag(row), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

            // When the counter is read by its description
            val counter = onAllNodes(waiting and inTag(row), useUnmergedTree = true).onFirst()
            val bounds = counter.fetchSemanticsNode().boundsInRoot
            val pixels = counter.pixelMap()

            // Then it is a pill (wider than high) in the warn tone, and the number is not read apart from it
            // (checked in the merged tree: the unmerged one still holds the text node that the description clears)
            (bounds.width > bounds.height) shouldBe true
            pixels.diagonalOnset(container).shouldNotBeNull()
            pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.status.warnBg.argb()
            onAllNodes(hasText("2") and inTag(row)).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `selected project row has accent frame`() =
        runComposeUiTest {
            // Given two projects at 4 px per dp, the busy one selected by a click
            showProjects(world()) { Scaled4 { ProjectsSidebar(it) } }
            awaitTag(ProjectsTags.row(busy.id)).performClick()
            waitForIdle()

            // When the rows are drawn
            val selected = awaitTag(ProjectsTags.row(busy.id))
            val other = awaitTag(ProjectsTags.row(quiet.id))
            val pixels = selected.pixelMap()

            // Then the selected row has the accent frame with the row radius r + 2 = 10 dp of plex, and the sel fill
            pixels.topEdgeColor(container) shouldBe colors.content.accent.argb()
            pixels.diagonalOnset(container).shouldNotBeNull().shouldBeInRange(10..12)
            selected.argbColorsOf() shouldContain colors.surfaces.sel.argb()
            // And the other row has neither
            other.argbColorsOf() shouldNotContain colors.content.accent.argb()
            other.argbColorsOf() shouldNotContain colors.surfaces.sel.argb()
        }

    @Test
    fun `sidebar title is uppercase label role`() =
        runComposeUiTest {
            showProjects(world()) { ProjectsSidebar(it) }
            awaitTag(ProjectsTags.row(busy.id))

            val style = onAllNodes(hasText("PROJEKTY"), useUnmergedTree = true).onFirst().textStyle()

            style.fontSize shouldBe 11.sp
            style.fontWeight shouldBe FontWeight.SemiBold
            style.letterSpacing shouldBe 0.12.em
            style.fontFamily shouldBe AmFonts.family("IBM Plex Sans")
        }

    @Test
    fun `add from disk opens the directory picker`() =
        runComposeUiTest {
            val world = world()
            world.picker.willCancel()
            showProjects(world) { ProjectsSidebar(it) }

            awaitTag(ProjectsTags.ADD_FROM_DISK).performClick()

            waitUntil { world.picker.picks == 1 }
        }

    @Test
    fun `add from disk shows the key cap`() =
        runComposeUiTest {
            showProjects(world()) { ProjectsSidebar(it, addKeyCap = "KEY!") }
            awaitTag(ProjectsTags.ADD_FROM_DISK)

            onAllNodes(hasText("Dodaj projekt z dysku…", substring = true) and inTag(ProjectsTags.ADD_FROM_DISK))
                .fetchSemanticsNodes()
                .isNotEmpty() shouldBe true
            onAllNodes(hasText("KEY!", substring = true) and inTag(ProjectsTags.ADD_FROM_DISK))
                .fetchSemanticsNodes()
                .isNotEmpty() shouldBe true
        }

    @Test
    fun `add from disk without key cap shows no key cap`() =
        runComposeUiTest {
            showProjects(world()) { ProjectsSidebar(it) }
            awaitTag(ProjectsTags.ADD_FROM_DISK)

            onAllNodes(hasText("⇧⌘O", substring = true)).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `add from disk sits under the project list`() =
        runComposeUiTest {
            showProjects(world()) { ProjectsSidebar(it) }

            val last = awaitTag(ProjectsTags.row(quiet.id)).getUnclippedBoundsInRoot()
            val add = awaitTag(ProjectsTags.ADD_FROM_DISK).getUnclippedBoundsInRoot()

            add.top shouldBeGreaterThanOrEqualTo last.bottom
        }

    @Test
    fun `add from disk never takes focus`() =
        runComposeUiTest {
            // Given the button clicked once and the chooser cancelled
            val world = world()
            world.picker.willCancel()
            showProjects(world) { ProjectsSidebar(it) }
            awaitTag(ProjectsTags.ADD_FROM_DISK).performClick()
            waitUntil { world.picker.picks == 1 }
            waitForIdle()

            // Then it holds no focus, so Enter and space do not click it again
            onNodeWithTag(ProjectsTags.ADD_FROM_DISK).assertIsNotFocused()
            press(Key.Enter)
            press(Key.Spacebar)
            world.picker.picks shouldBe 1
        }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.argbColorsOf(): Set<Int> = argbColors()
}
