package dev.quayside.projects.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.Outcome
import dev.quayside.projects.api.DirEntry
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The subfolders dialog on fakes (FR-P7, AM-306): the lines from the disk, the state of each, saving the checked ones.
 * `Carl` is a project of its own, `gone` is stored but no longer on the disk.
 */
@OptIn(ExperimentalTestApi::class)
class SubfoldersDialogScreenTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(
            id = "project0001",
            dir = plantDir,
            displayName = "Plant",
            subfolders = subfolderNames("bob", "gone"),
        )
    private val carlProject = aProject(id = "project0002", dir = plantDir.resolve("Carl"), displayName = "Carl project")
    private val anna = SubfolderName("Anna")
    private val bob = SubfolderName("bob")
    private val carl = SubfolderName("Carl")
    private val gone = SubfolderName("gone")

    private fun world(): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, carlProject))).also { world ->
            world.disk =
                { if (it == plantDir) listOf(DirEntry("bob"), DirEntry("Anna"), DirEntry("Carl")) else emptyList() }
        }

    private fun ComposeUiTest.open(world: ControllerWorld = world()): ProjectsController {
        val controller = showProjects(world) { ProjectsSidebar(it) }
        await(ProjectsTags.rowMenu(plant.id)).performClick()
        await(ProjectsTags.ROW_MENU_SUBFOLDERS).performClick()
        await(ProjectsTags.SUBFOLDERS_DIALOG)
        return controller
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

    @Test
    fun `dialog shows the lines with the stored ones checked`() =
        runComposeUiTest {
            open()

            await(ProjectsTags.subfolderCheck(anna)).assertIsOff()
            await(ProjectsTags.subfolderCheck(bob)).assertIsOn()
            await(ProjectsTags.subfolderCheck(gone)).assertIsOn()
        }

    @Test
    fun `a subfolder that is a project of its own is disabled and says so`() =
        runComposeUiTest {
            open()

            await(ProjectsTags.subfolderCheck(carl)).assertIsNotEnabled().assertIsOff()
            textIn(ProjectsTags.subfolderCheck(carl), "jest osobnym projektem").assertExists()
            await(ProjectsTags.subfolderCheck(anna)).assertIsEnabled()
        }

    @Test
    fun `clicking a disabled line checks nothing`() =
        runComposeUiTest {
            val controller = open()

            await(ProjectsTags.subfolderCheck(carl)).performClick()
            waitForIdle()

            onNodeWithTag(ProjectsTags.subfolderCheck(carl)).assertIsOff()
            (controller.state.value.dialog as Dialog.Subfolders).checked shouldBe setOf(bob, gone)
        }

    @Test
    fun `a subfolder gone from the disk is checked, says so and can be unchecked`() =
        runComposeUiTest {
            open()

            textIn(ProjectsTags.subfolderCheck(gone), "brak na dysku").assertExists()
            await(ProjectsTags.subfolderCheck(gone)).assertIsEnabled().performClick()
            waitForIdle()
            onNodeWithTag(ProjectsTags.subfolderCheck(gone)).assertIsOff()
        }

    @Test
    fun `subfolders dialog saves checked folders`() =
        runComposeUiTest {
            // Given the dialog of the group
            val world = world()
            open(world)

            // When the user checks Anna, unchecks the vanished folder and saves
            await(ProjectsTags.subfolderCheck(anna)).performClick()
            await(ProjectsTags.subfolderCheck(gone)).performClick()
            await(ProjectsTags.SUBFOLDERS_SAVE).performClick()
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.SUBFOLDERS_DIALOG))

            // Then the group has exactly the checked folders, sorted ignoring case
            world.projects.projects.value
                .first { it.id == plant.id }
                .subfolders shouldBe listOf(anna, bob)
        }

    @Test
    fun `subfolders dialog starts focus on Cancel`() =
        runComposeUiTest {
            open()

            waitUntilExactlyOneExists(hasTestTag(ProjectsTags.CANCEL) and isFocused())
        }

    @Test
    fun `Cancel closes the dialog without saving`() =
        runComposeUiTest {
            val world = world()
            open(world)
            await(ProjectsTags.subfolderCheck(anna)).performClick()

            await(ProjectsTags.CANCEL).performClick()
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.SUBFOLDERS_DIALOG))

            world.projects.calls shouldBe emptyList()
        }

    @Test
    fun `a save error stays in the dialog with its text`() =
        runComposeUiTest {
            val world = world()
            open(world)
            world.projects.failNext(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

            await(ProjectsTags.SUBFOLDERS_SAVE).performClick()

            textIn(ProjectsTags.SUBFOLDERS_DIALOG, "Nie udało się zapisać listy projektów.").assertExists()
            await(ProjectsTags.subfolderCheck(bob)).assertIsOn()
            world.projects.setSubfolders(plant.id, subfolderNames("bob")) shouldBe Outcome.Ok(Unit)
        }
}
