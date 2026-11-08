package dev.quayside.projects.ui

import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.SubfolderProblem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Saving, cancelling and failing in the subfolders dialog (FR-P7, AM-306): the checked names reach `setSubfolders`,
 * an error keeps the dialog, a late or failed listing opens nothing wrong.
 */
class ProjectsControllerSubfoldersSaveTest {
    private val plant = SubfolderFixture.plant
    private val other = SubfolderFixture.other
    private val plantDir = SubfolderFixture.plantDir
    private val bob = SubfolderFixture.bob
    private val anna = SubfolderFixture.anna

    private fun world(
        projects: List<dev.quayside.projects.api.Project> = listOf(plant, other, SubfolderFixture.carlProject),
        pathKind: (java.nio.file.Path) -> PathKind = { PathKind.DIRECTORY },
        gated: Boolean = false,
    ) = SubfolderFixture.world(projects, pathKind, gated)

    @Test
    fun `save stores the checked names and closes the dialog`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }

            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            world.projects.calls shouldBe listOf("setSubfolders project0001")
            world.projects.projects.value
                .first { it.id == plant.id }
                .subfolders shouldBe listOf(anna, bob)
            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `save with everything unchecked dissolves the group`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(bob)) }

            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            world.projects.projects.value
                .first { it.id == plant.id }
                .subfolders shouldBe emptyList()
        }

    @Test
    fun `save error keeps the subfolders dialog with its text`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }
            val failure = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)
            world.projects.failNext(failure)

            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            controller.subfoldersDialog() shouldBe
                Dialog.Subfolders(
                    project = plant,
                    choices = controller.subfoldersDialog().choices,
                    checked = setOf(anna, bob),
                    error = ProjectMessages.of(failure),
                )
            world.projects.projects.value
                .first { it.id == plant.id }
                .subfolders shouldBe listOf(bob)
        }

    @Test
    fun `save after an error tries again with the same names`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }
            world.projects.failNext(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            controller.state.value.dialog shouldBe null
            world.projects.projects.value
                .first { it.id == plant.id }
                .subfolders shouldBe listOf(anna, bob)
        }

    @Test
    fun `a rejected name shows its fixed text and keeps the dialog`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            val rejected = ProjectError.InvalidSubfolder(bob, SubfolderProblem.MISSING)
            world.projects.failNext(rejected)

            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            controller.subfoldersDialog().error shouldBe ProjectMessages.of(rejected)
        }

    @Test
    fun `toggling after an error clears the error`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            world.projects.failNext(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
            act { controller.onEvent(SidebarEvent.SaveSubfolders) }

            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }

            controller.subfoldersDialog().error shouldBe null
        }

    @Test
    fun `dismiss closes the dialog without saving`() =
        runTest {
            val world = world()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.ToggleSubfolder(anna)) }

            act { controller.onEvent(SidebarEvent.Dismiss) }

            controller.state.value.dialog shouldBe null
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `listing cancelled by dismiss opens nothing`() =
        runTest {
            val world = world()
            val late = world.inspector.holdListing()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.Dismiss) }

            late.complete(Unit)
            act { }

            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `a late listing does not replace the dialog opened after it`() =
        runTest {
            // Given the listing of the first project is still running after the user cancelled it
            val world = world()
            val late = world.inspector.holdListing()
            val controller = start(world)
            openSubfolders(controller)
            act { controller.onEvent(SidebarEvent.Dismiss) }

            // When the user opens the dialog of another project and the old listing finishes meanwhile
            openSubfolders(controller, other)
            val second = controller.state.value.dialog
            late.complete(Unit)
            act { }

            // Then the dialog of the second project is untouched (the reopening makes the guard observable)
            controller.state.value.dialog shouldBe second
            controller.subfoldersDialog().project shouldBe other
        }

    @Test
    fun `failed listing allows the next request`() =
        runTest {
            var missing = true
            val world = world(pathKind = { if (missing) PathKind.MISSING else PathKind.DIRECTORY })
            val controller = start(world)

            openSubfolders(controller)
            controller.state.value.dialog
                .shouldBeInstanceOf<Dialog.Message>()
            act { controller.onEvent(SidebarEvent.Dismiss) }
            missing = false
            openSubfolders(controller)

            controller.subfoldersDialog().project shouldBe plant
        }

    @Test
    fun `failed listing shows the fixed text of the error`() =
        runTest {
            val world = world(pathKind = { PathKind.MISSING })
            val controller = start(world)

            openSubfolders(controller)

            controller.state.value.dialog shouldBe
                Dialog.Message(ProjectMessages.of(ProjectError.NotFound(plantDir)))
        }
}
