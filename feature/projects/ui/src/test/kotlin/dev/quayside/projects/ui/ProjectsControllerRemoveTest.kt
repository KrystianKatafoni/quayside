package dev.quayside.projects.ui

import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Removing a project with a confirmation, also with running sessions (FR-P3, FR-P4; AM-304 plan, decision 1),
 * on the counts of the sessions snapshot (AM-305). Stopping through `stopAllIn` is in
 * [ProjectsControllerSessionsTest].
 */
class ProjectsControllerRemoveTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)

    /** Two active sessions of [project] (one in a subfolder), one ended, one active in a sibling folder. */
    private val running =
        listOf(
            WorldSession(aSessionId("working"), A_PROJECT_DIR),
            WorldSession(aSessionId("blocked"), A_PROJECT_DIR.resolve("src"), SessionState.AwaitingInput(null)),
            WorldSession(aSessionId("done"), A_PROJECT_DIR, SessionState.Done),
            WorldSession(aSessionId("sibling"), Path.of("/Users/test/projects/agent-manager-web")),
        )

    private fun world(sessions: List<WorldSession> = running): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)), running = sessions)

    /** Selects [project] and asks to remove it, as ⌘⌫ does. */
    private fun TestScope.requestRemove(controller: ProjectsController) {
        act { controller.onEvent(SidebarEvent.Select(project.id)) }
        act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }
    }

    @Test
    fun `remove without active sessions removes after confirmation`() =
        runTest {
            val world = world(running.filter { it.id.value == "done" })
            val controller = start(world)

            requestRemove(controller)
            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, active = SessionCounts(0, 0))
            world.projects.calls.shouldBeEmpty()

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }
            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.dialog shouldBe null
            controller.state.value.projects
                .shouldBeEmpty()
            world.sessions.calls.shouldBeEmpty()
        }

    @Test
    fun `stop and remove is ignored without active sessions`() =
        runTest {
            val world = world(running.filter { it.id.value == "done" })
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, active = SessionCounts(0, 0))
            world.sessions.calls.shouldBeEmpty()
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `keep sessions removes without stopping anything`() =
        runTest {
            val world = world()
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }

            world.sessions.calls.shouldBeEmpty()
            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.projects
                .shouldBeEmpty()
        }

    @Test
    fun `remove error shows a fixed message and keeps the project`() =
        runTest {
            val world = world()
            val controller = start(world)
            requestRemove(controller)
            val failure = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)
            world.projects.failNext(failure)

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }

            controller.state.value.projects shouldBe listOf(project)
            controller.state.value.dialog shouldBe Dialog.Message(ProjectMessages.of(failure))
        }

    @Test
    fun `cancel remove changes nothing`() =
        runTest {
            val world = world()
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.Dismiss) }

            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(project)
            controller.state.value.selected shouldBe project.id
            world.projects.calls.shouldBeEmpty()
            world.sessions.calls.shouldBeEmpty()
        }

    @Test
    fun `remove selected without selection does nothing`() =
        runTest {
            val world = world()
            val controller = start(world)

            act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }

            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(project)
            world.sessions.calls.shouldBeEmpty()
            world.projects.calls.shouldBeEmpty()
        }
}
