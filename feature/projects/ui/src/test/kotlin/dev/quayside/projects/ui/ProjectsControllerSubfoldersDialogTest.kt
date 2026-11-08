package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The subfolders dialog of the sidebar (FR-P7, AM-306): list the subdirectories, show the lines, check and uncheck.
 * The guards of the controller are tested here because the UI also disables the controls.
 */
class ProjectsControllerSubfoldersDialogTest {
    private val plant = SubfolderFixture.plant
    private val other = SubfolderFixture.other
    private val plantDir = SubfolderFixture.plantDir
    private val bob = SubfolderFixture.bob
    private val anna = SubfolderFixture.anna
    private val carl = SubfolderFixture.carl

    private fun world(
        projects: List<dev.quayside.projects.api.Project> = listOf(plant, other, SubfolderFixture.carlProject),
        pathKind: (java.nio.file.Path) -> PathKind = { PathKind.DIRECTORY },
        gated: Boolean = false,
    ) = SubfolderFixture.world(projects, pathKind, gated)

    @Test
    fun `request lists the subdirectories and opens the dialog with the stored ones checked`() =
        runTest {
            val world = world()
            val controller = start(world)

            openSubfolders(controller)

            world.inspector.listings shouldBe listOf(plantDir)
            controller.subfoldersDialog() shouldBe
                Dialog.Subfolders(
                    project = plant,
                    choices =
                        listOf(
                            SubfolderChoice(anna, ChoiceState.AVAILABLE),
                            SubfolderChoice(bob, ChoiceState.AVAILABLE),
                            SubfolderChoice(carl, ChoiceState.OWN_PROJECT),
                        ),
                    checked = setOf(bob),
                )
        }

    @Test
    fun `request from the palette opens the dialog of the selected project`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id)) }

            act { controller.onIntent(ProjectsIntent.EditSubfoldersRequested) }

            controller.subfoldersDialog().project shouldBe plant
        }

    @Test
    fun `request from the palette without a selection is ignored`() =
        runTest {
            val world = world()
            val controller = start(world)

            act { controller.onIntent(ProjectsIntent.EditSubfoldersRequested) }

            controller.state.value.dialog shouldBe null
            world.inspector.listings.shouldBeEmpty()
        }

    @Test
    fun `request for an unlisted project is ignored`() =
        runTest {
            val controller = start(world())

            act { controller.onEvent(SidebarEvent.EditSubfoldersRequested(ProjectId("nope"))) }

            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `a stored subfolder that the disk no longer has is shown checked and missing`() =
        runTest {
            val gone = plant.copy(subfolders = subfolderNames("bob", "gone"))
            val controller = start(world(projects = listOf(gone, other)))

            openSubfolders(controller, gone)

            controller.subfoldersDialog().choices shouldBe
                listOf(
                    SubfolderChoice(anna, ChoiceState.AVAILABLE),
                    SubfolderChoice(bob, ChoiceState.AVAILABLE),
                    SubfolderChoice(carl, ChoiceState.AVAILABLE),
                    SubfolderChoice(SubfolderName("gone"), ChoiceState.MISSING),
                )
            controller.subfoldersDialog().checked shouldBe setOf(bob, SubfolderName("gone"))
        }

    @Test
    fun `listing shows a waiting state until the disk answers`() =
        runTest {
            val world = world(gated = true)
            val controller = start(world)

            openSubfolders(controller)

            controller.state.value.dialog shouldBe Dialog.Waiting.LISTING
            world.inspector.release()
            act { }
            controller.subfoldersDialog().project shouldBe plant
        }

    @Test
    fun `toggle checks and unchecks a line`() =
        runTest {
            val controller = start(world())
            openSubfolders(controller)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }
            controller.subfoldersDialog().checked shouldBe setOf(bob, anna)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(bob)) }
            controller.subfoldersDialog().checked shouldBe setOf(anna)
        }

    @Test
    fun `a subfolder that is a project of its own cannot be checked`() =
        runTest {
            val controller = start(world())
            openSubfolders(controller)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(carl)) }

            controller.subfoldersDialog().checked shouldBe setOf(bob)
        }

    @Test
    fun `a missing subfolder can be unchecked`() =
        runTest {
            val gone = plant.copy(subfolders = subfolderNames("gone"))
            val controller = start(world(projects = listOf(gone, other)))
            openSubfolders(controller, gone)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(SubfolderName("gone"))) }

            controller.subfoldersDialog().checked shouldBe emptySet()
        }

    @Test
    fun `toggle of a name that is not a line is ignored`() =
        runTest {
            val controller = start(world())
            openSubfolders(controller)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(SubfolderName("stranger"))) }

            controller.subfoldersDialog().checked shouldBe setOf(bob)
        }

    @Test
    fun `toggle and save outside the dialog are ignored`() =
        runTest {
            val world = world()
            val controller = start(world)

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(bob)) }
            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            controller.state.value.dialog shouldBe null
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `edit subfolders is ignored while a dialog is open`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(other.id)) }
            val rename = controller.state.value.dialog

            act { controller.onEvent(SidebarEvent.EditSubfoldersRequested(plant.id)) }
            act { controller.onIntent(ProjectsIntent.EditSubfoldersRequested) }

            controller.state.value.dialog shouldBe rename
            world.inspector.listings.shouldBeEmpty()
        }

    @Test
    fun `a second request while the dialog is open is ignored`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            val first = controller.state.value.dialog

            act { controller.onEvent(SidebarEvent.EditSubfoldersRequested(other.id)) }

            controller.state.value.dialog shouldBe first
            world.inspector.listings shouldBe listOf(plantDir)
        }

    @Test
    fun `menu allows editing subfolders only with a selection and no dialog`() =
        runTest {
            val controller = start(world())
            controller.menu.value.canEditSubfolders shouldBe false

            act { controller.onEvent(SidebarEvent.Select(plant.id)) }
            controller.menu.value.canEditSubfolders shouldBe true

            openSubfolders(controller)
            controller.menu.value.canEditSubfolders shouldBe false

            act { controller.onEvent(SidebarEvent.Dismiss) }
            act { controller.onIntent(ProjectsIntent.ClearSelection) }
            controller.menu.value.canEditSubfolders shouldBe false
        }

    @Test
    fun `a stored subfolder that became a project of its own stays available and can be unchecked`() =
        runTest {
            val stored = plant.copy(subfolders = subfolderNames("bob", "Carl"))
            val controller = start(world(projects = listOf(stored, other, SubfolderFixture.carlProject)))
            openSubfolders(controller, stored)

            controller
                .subfoldersDialog()
                .choices
                .first { it.name == carl }
                .state shouldBe ChoiceState.AVAILABLE

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(carl)) }
            controller.subfoldersDialog().checked shouldBe setOf(bob)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(carl)) }
            controller.subfoldersDialog().checked shouldBe setOf(bob, carl)
        }
}
