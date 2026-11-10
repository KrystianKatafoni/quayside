package dev.quayside.projects.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The context menu of a project row (FR-P3, FR-P6, FR-P7): a right click on the row opens it, there is no "⋯" button.
 * Names (`Plant`, `Anna`) differ from fixed texts of the UI.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsRowMenuTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(id = "project0001", dir = plantDir, displayName = "Plant", subfolders = subfolderNames("Anna"))
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")
    private val annaRow = ProjectsTags.subfolderRow(plant.id, subfolderNames("Anna").first())
    private val menuTags =
        listOf(ProjectsTags.ROW_MENU_RENAME, ProjectsTags.ROW_MENU_SUBFOLDERS, ProjectsTags.ROW_MENU_REMOVE)

    private fun ComposeUiTest.show(): ProjectsController =
        showProjects(
            ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, plain))),
        ) { ProjectsSidebar(it) }

    private fun ComposeUiTest.menuIsGone() = waitUntilDoesNotExist(hasTestTag(ProjectsTags.ROW_MENU_REMOVE))

    @Test
    fun `right click on a project row opens the menu with three items`() =
        runComposeUiTest {
            show()

            openRowMenu(plant.id)

            menuTags.forEach { awaitTag(it).assertIsDisplayed() }
            onAllNodes(
                hasTestTag(ProjectsTags.ROW_MENU_RENAME) or hasTestTag(ProjectsTags.ROW_MENU_SUBFOLDERS) or
                    hasTestTag(ProjectsTags.ROW_MENU_REMOVE),
            ).assertCountEquals(3)
        }

    @Test
    fun `left click selects the project and does not open the menu`() =
        runComposeUiTest {
            val controller = show()

            awaitTag(ProjectsTags.row(plant.id)).performClick()
            waitForIdle()

            controller.focus.value shouldBe ProjectFocus(plant.id)
            onNodeWithTag(ProjectsTags.row(plant.id)).assertIsSelected()
            onAllNodes(hasTestTag(ProjectsTags.ROW_MENU_REMOVE)).assertCountEquals(0)
        }

    @Test
    fun `right click on a subfolder row does not open the project menu`() =
        runComposeUiTest {
            show()

            awaitTag(annaRow).performMouseInput { rightClick() }
            waitForIdle()

            onAllNodes(hasTestTag(ProjectsTags.ROW_MENU_REMOVE)).assertCountEquals(0)
        }

    @Test
    fun `Esc closes the menu and leaves no focus behind`() =
        runComposeUiTest {
            show()
            openRowMenu(plant.id)
            awaitTag(ProjectsTags.ROW_MENU_REMOVE)

            awaitTag(ProjectsTags.ROW_MENU_RENAME).performKeyInput { pressKey(Key.Escape) }

            menuIsGone()
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    @Test
    fun `click outside the menu closes it`() =
        runComposeUiTest {
            show()
            openRowMenu(plant.id)
            awaitTag(ProjectsTags.ROW_MENU_REMOVE)

            onAllNodes(isRoot()).onFirst().performMouseInput { click(topLeft) }
            waitForIdle()

            menuIsGone()
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    @Test
    fun `picking an item closes the menu and runs its action`() =
        runComposeUiTest {
            show()
            openRowMenu(plant.id)

            awaitTag(ProjectsTags.ROW_MENU_RENAME).performClick()

            awaitTag(ProjectsTags.RENAME_DIALOG)
            menuIsGone()
            onAllNodes(hasTestTag(ProjectsTags.ROW_MENU_RENAME)).assertCountEquals(0)
        }

    @Test
    fun `the row has no dots button`() =
        runComposeUiTest {
            show()

            awaitTag(ProjectsTags.row(plant.id))
            onAllNodes(hasText("⋯", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
}
