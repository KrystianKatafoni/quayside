package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.NameProblem
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Changing the display name of a project from the row menu (FR-P6; AM-305 plan, decision 4). */
class ProjectsControllerRenameTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    private fun world(): ControllerWorld = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project, other)))

    @Test
    fun `rename opens with the current name`() =
        runTest {
            val controller = start(world())

            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }

            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "Quayside")
        }

    @Test
    fun `rename of an unknown project does nothing`() =
        runTest {
            val controller = start(world())

            act { controller.onEvent(SidebarEvent.RenameRequested(ProjectId("gone"))) }

            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `confirming renames and closes the dialog`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }

            act { controller.onEvent(SidebarEvent.NameChanged("  Nowa nazwa  ")) }
            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "  Nowa nazwa  ")
            act { controller.onEvent(SidebarEvent.ConfirmRename) }

            world.projects.calls shouldBe listOf("rename project0001")
            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(project.copy(displayName = "Nowa nazwa"), other)
        }

    @Test
    fun `cancel closes the dialog without writing`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("Other name")) }

            act { controller.onEvent(SidebarEvent.Dismiss) }

            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(project, other)
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `unchanged name closes without writing`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("  Quayside ")) }

            act { controller.onEvent(SidebarEvent.ConfirmRename) }

            controller.state.value.dialog shouldBe null
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `invalid name keeps the draft with a fixed message`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("   ")) }

            act { controller.onEvent(SidebarEvent.ConfirmRename) }

            controller.state.value.dialog shouldBe
                Dialog.Rename(
                    project,
                    draft = "   ",
                    error = ProjectMessages.of(ProjectError.InvalidDisplayName(NameProblem.BLANK)),
                )
            controller.state.value.projects shouldBe listOf(project, other)
        }

    @Test
    fun `typing clears the error`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("")) }
            act { controller.onEvent(SidebarEvent.ConfirmRename) }
            (controller.state.value.dialog as Dialog.Rename).error shouldBe
                ProjectMessages.of(ProjectError.InvalidDisplayName(NameProblem.BLANK))

            act { controller.onEvent(SidebarEvent.NameChanged("N")) }

            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "N", error = null)
        }

    @Test
    fun `storage failure keeps the dialog`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("Nowa nazwa")) }
            val failure = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)
            world.projects.failNext(failure)

            act { controller.onEvent(SidebarEvent.ConfirmRename) }

            controller.state.value.dialog shouldBe
                Dialog.Rename(project, draft = "Nowa nazwa", error = ProjectMessages.of(failure))
            controller.state.value.projects shouldBe listOf(project, other)
        }

    @Test
    fun `rename while a dialog is open is ignored`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }

            act { controller.onEvent(SidebarEvent.RenameRequested(other.id)) }

            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "Quayside")
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `a second confirm before the first runs is ignored`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            act { controller.onEvent(SidebarEvent.NameChanged("Nowa nazwa")) }

            act {
                controller.onEvent(SidebarEvent.ConfirmRename)
                controller.onEvent(SidebarEvent.ConfirmRename)
            }

            world.projects.calls shouldBe listOf("rename project0001")
            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `menu disables remove while renaming`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(project.id)) }
            controller.menu.value shouldBe
                ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)

            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }

            controller.menu.value shouldBe
                ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)
        }

    @Test
    fun `logs never contain the name`() =
        runTest {
            val secret = "secret-name"
            val world =
                ControllerWorld(
                    projects = FakeProjectsApi(initial = listOf(project)),
                    running = listOf(WorldSession(aSessionId("working"), A_PROJECT_DIR)),
                )
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.RenameRequested(project.id)) }
            world.projects.failNext(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
            act { controller.onEvent(SidebarEvent.NameChanged(secret)) }
            act { controller.onEvent(SidebarEvent.ConfirmRename) }
            act { controller.onEvent(SidebarEvent.ConfirmRename) }

            world.projects.calls.shouldNotBeEmpty()
            world.logger.entries.forEach { entry ->
                withClue(entry) {
                    entry.message shouldNotContain secret
                    entry.fields.values.forEach { it.toString() shouldNotContain secret }
                    entry.cause?.message.orEmpty() shouldNotContain secret
                }
            }
        }
}
