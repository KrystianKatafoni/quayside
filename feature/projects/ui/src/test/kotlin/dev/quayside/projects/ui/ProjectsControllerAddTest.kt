package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence
import dev.quayside.projects.api.InspectionWorld
import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.aProject
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Adding a folder from disk (FR-P1, FR-P2; AM-304 plan, decision 2). */
class ProjectsControllerAddTest {
    private val inspection = InspectionWorld(git = GitInfo("main"), claudeMd = true, sessions = 1)
    private val report = DirectoryReport(A_PROJECT_DIR, GitInfo("main"), true, 1, HooksPresence.NONE, null)

    @Test
    fun `cancelled picker changes nothing`() =
        runTest {
            val world = ControllerWorld()
            val controller = start(world)
            world.picker.willCancel()

            act { controller.onIntent(ProjectsIntent.AddRequested) }

            world.picker.picks shouldBe 1
            world.inspector.inspected.shouldBeEmpty()
            world.projects.calls.shouldBeEmpty()
            controller.state.value shouldBe ProjectsUiState(sessions = NO_ACTIVE_SESSIONS)
            controller.menu.value shouldBe
                ProjectsMenuState(canAdd = true, canRemove = false, canRename = false, dialogOpen = false)
        }

    @Test
    fun `picked folder is inspected before it is added`() =
        runTest {
            val world = ControllerWorld(inspection = inspection)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)

            act { controller.onIntent(ProjectsIntent.AddRequested) }

            world.inspector.inspected.map(Path::toString) shouldBe listOf(A_PROJECT_DIR.toString())
            world.projects.calls.shouldBeEmpty()
            controller.state.value.dialog shouldBe Dialog.ConfirmAdd(report)
        }

    @Test
    fun `inspection shows checking until the report arrives`() =
        runTest {
            val world = ControllerWorld(inspection = inspection, gatedInspection = true)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)

            act { controller.onIntent(ProjectsIntent.AddRequested) }
            controller.state.value.dialog shouldBe Dialog.Waiting.INSPECTING

            act { world.inspector.release() }
            controller.state.value.dialog shouldBe Dialog.ConfirmAdd(report)
        }

    @Test
    fun `cancelling the inspection drops its report`() =
        runTest {
            val world = ControllerWorld(inspection = inspection, gatedInspection = true)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            act { controller.onEvent(SidebarEvent.Dismiss) }
            act { world.inspector.release() }

            controller.state.value.dialog
                .shouldBeNull()
            world.projects.calls.shouldBeEmpty()
            controller.menu.value.canAdd shouldBe true
        }

    @Test
    fun `confirming adds the folder and selects it`() =
        runTest {
            val world = ControllerWorld(inspection = inspection)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            act { controller.onEvent(SidebarEvent.ConfirmAdd) }

            val added = Project(ProjectId("project0001"), A_PROJECT_DIR, "agent-manager")
            world.projects.calls shouldBe listOf("add $A_PROJECT_DIR")
            controller.state.value shouldBe
                ProjectsUiState(projects = listOf(added), selected = added.id, sessions = NO_ACTIVE_SESSIONS)
        }

    @Test
    fun `a second confirm before the first runs is ignored`() =
        runTest {
            val world = ControllerWorld(inspection = inspection)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            act {
                controller.onEvent(SidebarEvent.ConfirmAdd)
                controller.onEvent(SidebarEvent.ConfirmAdd)
            }

            world.projects.calls shouldBe listOf("add $A_PROJECT_DIR")
            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `already added folder selects the existing project without adding`() =
        runTest {
            val existing = aProject(id = "existing", dir = A_PROJECT_DIR)
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(existing)))
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)

            act { controller.onIntent(ProjectsIntent.AddRequested) }

            world.projects.calls.shouldBeEmpty()
            controller.state.value.selected shouldBe existing.id
            controller.state.value.dialog shouldBe
                Dialog.Message(ProjectMessages.of(ProjectError.AlreadyAdded(existing.id)))
        }

    @Test
    fun `inspection error shows a fixed message`() =
        runTest {
            val world = ControllerWorld(pathKind = { PathKind.MISSING })
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)

            act { controller.onIntent(ProjectsIntent.AddRequested) }
            controller.state.value.dialog shouldBe
                Dialog.Message(ProjectMessages.of(ProjectError.NotFound(A_PROJECT_DIR)))

            act { controller.onEvent(SidebarEvent.Dismiss) }
            controller.state.value shouldBe ProjectsUiState(sessions = NO_ACTIVE_SESSIONS)
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `add error keeps the dialog with a fixed message`() =
        runTest {
            val world = ControllerWorld(inspection = inspection)
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }
            val failure = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)
            world.projects.failNext(failure)

            act { controller.onEvent(SidebarEvent.ConfirmAdd) }

            controller.state.value.dialog shouldBe Dialog.ConfirmAdd(report, error = ProjectMessages.of(failure))
            controller.state.value.projects
                .shouldBeEmpty()
            controller.state.value.selected
                .shouldBeNull()
        }

    @Test
    fun `requests while a dialog is open are ignored`() =
        runTest {
            val world = ControllerWorld(projects = FakeProjectsApi(listOf(aProject())), gatedInspection = true)
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(aProject().id)) }
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            act { controller.onIntent(ProjectsIntent.AddRequested) }
            act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }

            world.picker.picks shouldBe 1
            world.inspector.inspected.map(Path::toString) shouldBe listOf(A_PROJECT_DIR.toString())
            world.sessions.calls.shouldBeEmpty()
            controller.state.value.dialog shouldBe Dialog.Waiting.INSPECTING
        }

    @Test
    fun `requests while the folder chooser is open are ignored`() =
        runTest {
            val world = ControllerWorld()
            val controller = start(world)

            act { controller.onIntent(ProjectsIntent.AddRequested) }
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            world.picker.picks shouldBe 1
            controller.menu.value shouldBe
                ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)
        }
}
