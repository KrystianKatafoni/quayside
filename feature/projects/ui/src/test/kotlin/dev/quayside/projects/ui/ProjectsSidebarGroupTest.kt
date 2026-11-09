package dev.quayside.projects.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * A project with subfolders in the sidebar (FR-P7, AM-306) on fakes: the group row with its chevron, the subfolder
 * rows with their own counters, selecting a subfolder. Names (`Anna`, `bob`, `plant-puls`) differ from fixed texts.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsSidebarGroupTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(id = "project0001", dir = plantDir, displayName = "Plant", subfolders = subfolderNames("bob", "Anna"))
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")
    private val anna = SubfolderName("Anna")
    private val bob = SubfolderName("bob")

    // Anna: one session that waits; bob: one that works; the project directory: one that works
    private val sessions =
        listOf(
            WorldSession(aSessionId("asks"), plantDir.resolve("Anna/x"), SessionState.AwaitingInput(null)),
            WorldSession(aSessionId("works"), plantDir.resolve("bob")),
            WorldSession(aSessionId("rooted"), plantDir),
        )

    private fun world(): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, plain)), running = sessions)

    private fun ComposeUiTest.show(world: ControllerWorld = world()): ProjectsController =
        showProjects(world) { ProjectsSidebar(it) }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.gone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun chip(
        tag: String,
        description: String,
    ) = hasContentDescription(description, substring = true) and hasAnyAncestor(hasTestTag(tag))

    private fun ComposeUiTest.awaitChip(
        tag: String,
        description: String,
    ) = waitUntil { onAllNodes(chip(tag, description)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.hasChip(
        tag: String,
        description: String,
    ): Boolean = onAllNodes(chip(tag, description)).fetchSemanticsNodes().isNotEmpty()

    private val annaRow get() = ProjectsTags.subfolderRow(plant.id, anna)
    private val bobRow get() = ProjectsTags.subfolderRow(plant.id, bob)

    @Test
    fun `group row shows subfolder rows with counters`() =
        runComposeUiTest {
            // Given a group with sessions in its subfolders and in its directory
            show()

            // Then the rows of the subfolders are there, sorted ignoring case, under the group row
            await(ProjectsTags.row(plant.id))
            await(annaRow)
            await(bobRow)
            val group = onNodeWithTag(ProjectsTags.row(plant.id)).fetchSemanticsNode().boundsInRoot
            val first = onNodeWithTag(annaRow).fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag(bobRow).fetchSemanticsNode().boundsInRoot
            (group.top < first.top && first.top < second.top) shouldBe true
            onAllNodes(hasText("Anna", substring = true) and hasAnyAncestor(hasTestTag(annaRow)) or hasTestTag(annaRow))
                .onFirst()
                .assertExists()

            // And each subfolder counts only its own sessions, the group row counts the whole project
            awaitChip(annaRow, "czeka: 1")
            awaitChip(bobRow, "pracuje: 1")
            hasChip(annaRow, "pracuje") shouldBe false
            hasChip(bobRow, "czeka") shouldBe false
            awaitChip(ProjectsTags.row(plant.id), "czeka: 1")
            awaitChip(ProjectsTags.row(plant.id), "pracuje: 2")
        }

    @Test
    fun `project without subfolders has neither a chevron nor subfolder rows`() =
        runComposeUiTest {
            show()

            await(ProjectsTags.row(plain.id))

            onAllNodes(hasTestTag(ProjectsTags.chevron(plain.id))).assertCountEquals(0)
            onAllNodes(hasTestTag(ProjectsTags.chevron(plant.id))).assertCountEquals(1)
        }

    @Test
    fun `chevron collapses and expands the group`() =
        runComposeUiTest {
            show()
            await(annaRow)
            await(ProjectsTags.chevron(plant.id)).assertContentDescriptionEquals("Zwiń grupę")

            // When the user clicks the chevron
            await(ProjectsTags.chevron(plant.id)).performClick()

            // Then the subfolder rows are hidden and the label offers to expand
            gone(annaRow)
            gone(bobRow)
            await(ProjectsTags.row(plant.id))
            await(ProjectsTags.chevron(plant.id)).assertContentDescriptionEquals("Rozwiń grupę")

            // And a second click shows them again
            await(ProjectsTags.chevron(plant.id)).performClick()
            await(annaRow)
            await(bobRow)
            await(ProjectsTags.chevron(plant.id)).assertContentDescriptionEquals("Zwiń grupę")
        }

    @Test
    fun `collapsed group still counts the sessions of its subfolders on the group row`() =
        runComposeUiTest {
            show()
            await(annaRow)

            await(ProjectsTags.chevron(plant.id)).performClick()
            gone(annaRow)

            awaitChip(ProjectsTags.row(plant.id), "czeka: 1")
            awaitChip(ProjectsTags.row(plant.id), "pracuje: 2")
        }

    @Test
    fun `subfolder click selects it and the chevron and rows never take focus`() =
        runComposeUiTest {
            // Given a group
            val controller = show()
            await(annaRow)

            // When the user clicks a subfolder row
            onNodeWithTag(annaRow).performClick()
            waitForIdle()

            // Then that subfolder is selected, the other one is not, and the row holds no focus
            controller.focus.value shouldBe ProjectFocus(plant.id, anna)
            onNodeWithTag(annaRow).assertIsSelected()
            onNodeWithTag(bobRow).assertIsNotSelected()
            onNodeWithTag(annaRow).assertIsNotFocused()

            // And Tab, Enter and space find nothing to focus, so they reach the window
            press(Key.Tab)
            onAllNodes(isFocused()).assertCountEquals(0)
            press(Key.Enter)
            press(Key.Spacebar)
            controller.focus.value shouldBe ProjectFocus(plant.id, anna)
        }

    @Test
    fun `Enter and space after clicking the chevron do not toggle the group again`() =
        runComposeUiTest {
            // Given the group collapsed by a click on its chevron
            show()
            await(annaRow)
            await(ProjectsTags.chevron(plant.id)).performClick()
            gone(annaRow)
            onNodeWithTag(ProjectsTags.chevron(plant.id)).assertIsNotFocused()

            // When the user presses Enter and space
            press(Key.Enter)
            press(Key.Spacebar)

            // Then the group stays collapsed and nothing holds the focus
            onAllNodes(hasTestTag(annaRow)).assertCountEquals(0)
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    @Test
    fun `project row click after a subfolder goes back to the project`() =
        runComposeUiTest {
            val controller = show()
            await(annaRow).performClick()
            waitForIdle()

            await(ProjectsTags.row(plant.id)).performClick()
            waitForIdle()

            controller.focus.value shouldBe ProjectFocus(plant.id)
            onNodeWithTag(ProjectsTags.row(plant.id)).assertIsSelected()
            onNodeWithTag(annaRow).assertIsNotSelected()
        }

    @Test
    fun `collapsing the group of the selected subfolder selects the project row`() =
        runComposeUiTest {
            val controller = show()
            await(annaRow).performClick()
            waitForIdle()

            await(ProjectsTags.chevron(plant.id)).performClick()
            gone(annaRow)

            controller.focus.value shouldBe ProjectFocus(plant.id)
            onNodeWithTag(ProjectsTags.row(plant.id)).assertIsSelected()
        }

    @Test
    fun `row menu offers the subfolders dialog`() =
        runComposeUiTest {
            show()

            await(ProjectsTags.rowMenu(plant.id)).performClick()
            await(ProjectsTags.ROW_MENU_SUBFOLDERS).performClick()

            await(ProjectsTags.SUBFOLDERS_DIALOG)
        }
}
