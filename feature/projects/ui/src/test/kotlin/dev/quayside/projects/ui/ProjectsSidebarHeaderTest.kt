package dev.quayside.projects.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Header with "+", project rows with folder and count chips (plan AM-809). */
@OptIn(ExperimentalTestApi::class)
internal class ProjectsSidebarHeaderTest {
    private val busy = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Busy")
    private val quiet = aProject(id = "project0002", dir = Path.of("/Users/test/projects/quiet"), displayName = "Quiet")
    private val colors = SampleTokens.colors()

    private fun world(vararg sessions: WorldSession) =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(busy, quiet)), running = sessions.toList())

    /** How many nodes under [row] have [description] as their content description. */
    private fun ComposeUiTest.described(
        row: String,
        description: String,
    ): Int =
        onAllNodes(hasContentDescription(description) and hasAnyAncestor(hasTestTag(row)))
            .fetchSemanticsNodes()
            .size

    @Test
    fun `add button in the header opens the directory picker`() =
        runComposeUiTest {
            // Given the sidebar; the folder chooser answers with a cancel
            val world = world()
            world.picker.willCancel()
            showProjects(world) { ProjectsSidebar(it) }

            // Then "+" is an icon button announced as "Dodaj projekt", above the project list
            val add = awaitTag(ProjectsTags.ADD_BUTTON)
            add.assertContentDescriptionEquals(ProjectsStrings["sidebar.add"])
            val addBounds = add.getUnclippedBoundsInRoot()
            val firstRow = awaitTag(ProjectsTags.row(busy.id)).getUnclippedBoundsInRoot()
            addBounds.bottom shouldBeLessThanOrEqualTo firstRow.top

            // When the user clicks it, then the chooser opens
            add.performClick()
            waitUntil { world.picker.picks == 1 }
        }

    @Test
    fun `sidebar has one plus button and no text button named add project`() =
        runComposeUiTest {
            showProjects(world()) { ProjectsSidebar(it) }
            awaitTag(ProjectsTags.row(quiet.id))

            // One add button only, and no text button "Dodaj projekt" under the list
            onAllNodes(hasTestTag(ProjectsTags.ADD_BUTTON)).fetchSemanticsNodes().size shouldBe 1
            onAllNodes(hasText(ProjectsStrings["sidebar.add"])).fetchSemanticsNodes().size shouldBe 0
            val last = awaitTag(ProjectsTags.row(quiet.id)).getUnclippedBoundsInRoot()
            awaitTag(ProjectsTags.ADD_BUTTON).getUnclippedBoundsInRoot().top shouldBeLessThanOrEqualTo last.top
        }

    @Test
    fun `project row shows folder in project tone and count chips with descriptions`() =
        runComposeUiTest {
            // Given a project with 2 waiting and 3 working sessions, and a quiet one
            val sessions =
                (1..2).map { WorldSession(aSessionId("w$it"), A_PROJECT_DIR, SessionState.AwaitingInput(null)) } +
                    (1..3).map { WorldSession(aSessionId("p$it"), A_PROJECT_DIR, SessionState.Working) }
            showProjects(world(*sessions.toTypedArray())) { ProjectsSidebar(it) }
            val row = ProjectsTags.row(busy.id)
            awaitTag(row)
            waitUntil { described(row, "czeka: 2") > 0 }

            // Then the chips are described with the old counter texts and show only the numbers
            described(row, "pracuje: 3") shouldBe 1
            onAllNodes(hasText("czeka: 2", substring = true) and hasAnyAncestor(hasTestTag(row)))
                .fetchSemanticsNodes()
                .size shouldBe 0

            // And the busy row draws the warn tone while the quiet row draws no status tone
            val warn =
                colors.status.warn
                    .toColor()
                    .toArgb()
            val work =
                colors.status.work
                    .toColor()
                    .toArgb()
            awaitTag(row).argbColors() shouldContain warn
            val quietColors = awaitTag(ProjectsTags.row(quiet.id)).argbColors()
            quietColors shouldNotContain warn
            quietColors shouldNotContain work
        }
}
