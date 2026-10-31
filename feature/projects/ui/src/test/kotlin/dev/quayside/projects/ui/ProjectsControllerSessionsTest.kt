@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.projects.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Per-project session counts and removing through `SessionCommands.stopAllIn` (FR-P6, FR-P4; AM-305 plan,
 * decisions 1-3). The fake sessions feature is fresh from the start; `runtimeAvailable(false)` makes the
 * snapshot stale.
 */
class ProjectsControllerSessionsTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)
    private val otherDir: Path = Path.of("/Users/test/projects/other")
    private val other = aProject(id = "project0002", dir = otherDir, displayName = "other")

    private val working = WorldSession(aSessionId("working"), A_PROJECT_DIR)
    private val blocked =
        WorldSession(aSessionId("blocked"), A_PROJECT_DIR.resolve("src"), SessionState.AwaitingInput(null))
    private val done = WorldSession(aSessionId("done"), A_PROJECT_DIR, SessionState.Done)
    private val sibling = WorldSession(aSessionId("sibling"), Path.of("/Users/test/projects/agent-manager-web"))

    private fun world(vararg sessions: WorldSession): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(project, other)), running = sessions.toList())

    private fun TestScope.requestRemove(controller: ProjectsController) {
        act { controller.onEvent(SidebarEvent.Select(project.id)) }
        act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }
    }

    @Test
    fun `counts follow the snapshot`() =
        runTest {
            val world = world(working, blocked, done, sibling)
            val controller = start(world)
            controller.state.value.sessions shouldBe freshView(project.id to SessionCounts(working = 1, waiting = 1))

            world.sessions.setState(working.id, SessionState.AwaitingPermission("Bash"))
            runCurrent()
            controller.state.value.sessions shouldBe freshView(project.id to SessionCounts(working = 0, waiting = 2))

            world.sessions.runtimeAvailable(false)
            runCurrent()
            controller.state.value.sessions shouldBe
                SessionsView(mapOf(project.id to SessionCounts(working = 0, waiting = 2)), fresh = false)

            world.sessions.runtimeAvailable(true)
            runCurrent()
            controller.state.value.sessions.fresh shouldBe true
        }

    @Test
    fun `counts follow a renamed or removed project`() =
        runTest {
            val inOther = WorldSession(aSessionId("elsewhere"), otherDir)
            val world = world(working, inOther)
            val controller = start(world)
            controller.state.value.sessions shouldBe
                freshView(project.id to SessionCounts(1, 0), other.id to SessionCounts(1, 0))

            world.projects.rename(project.id, "Renamed")
            runCurrent()
            controller.state.value.sessions shouldBe
                freshView(project.id to SessionCounts(1, 0), other.id to SessionCounts(1, 0))

            world.projects.remove(project.id)
            runCurrent()
            controller.state.value.sessions shouldBe freshView(other.id to SessionCounts(1, 0))
        }

    @Test
    fun `remove dialog opens without listing the runtime`() =
        runTest {
            val world = world(working, blocked)
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(project.id)) }

            // No runCurrent: the dialog is open the moment the intent returns.
            controller.onIntent(ProjectsIntent.RemoveSelectedRequested)

            controller.state.value.dialog shouldBe
                Dialog.ConfirmRemove(project, SessionCounts(working = 1, waiting = 1))
            runCurrent()
            world.sessions.calls.shouldBeEmpty()
        }

    @Test
    fun `remove with a stale snapshot offers only remove from list`() =
        runTest {
            val world = world(working)
            val controller = start(world)
            world.sessions.runtimeAvailable(false)
            runCurrent()

            requestRemove(controller)
            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, active = null)

            act { controller.onEvent(SidebarEvent.StopAndRemove) }
            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, active = null)
            world.sessions.calls.shouldBeEmpty()
            world.projects.calls.shouldBeEmpty()

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }
            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.dialog shouldBe null
            world.sessions.calls.shouldBeEmpty()
        }

    @Test
    fun `remove with active sessions offers stop and keep`() =
        runTest {
            val world = world(working, blocked, done, sibling)
            val controller = start(world)

            requestRemove(controller)

            controller.state.value.dialog shouldBe
                Dialog.ConfirmRemove(project, SessionCounts(working = 1, waiting = 1))
            world.sessions.calls.shouldBeEmpty()
        }

    @Test
    fun `stop and remove calls stopAllIn once and then removes`() =
        runTest {
            val world = world(working, blocked, done, sibling)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(other)
            world.logger.entries.any { it.fields["sessionsStopped"] == 2 } shouldBe true
        }

    @Test
    fun `stopAllIn picks up a session started after the dialog opened`() =
        runTest {
            val world = world(working)
            val controller = start(world)
            requestRemove(controller)
            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, SessionCounts(1, 0))

            world.sessions.appear(WorldSession(aSessionId("late"), A_PROJECT_DIR))
            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.sessions.snapshot.value
                .countsFor(project.id) shouldBe SessionCounts(0, 0)
            world.projects.calls shouldBe listOf("remove project0001")
        }

    @Test
    fun `incomplete stop keeps the project and shows a message`() =
        runTest {
            val world = world(working, blocked)
            val controller = start(world)
            requestRemove(controller)
            world.sessions.failStop(working.id)

            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            val error = SessionError.StopIncomplete(stopped = 1, failed = listOf(working.id))
            world.projects.calls.shouldBeEmpty()
            controller.state.value.projects shouldBe listOf(project, other)
            controller.state.value.dialog shouldBe Dialog.Message(StopMessages.of(error))
            val warning = world.logger.entriesAt(LogLevel.WARN).single()
            warning.fields["reason"] shouldBe StopMessages.category(error)
            warning.fields["failed"] shouldBe 1
        }

    @Test
    fun `runtime failure keeps the project and shows a fixed message`() =
        runTest {
            val world = world(working)
            val controller = start(world)
            requestRemove(controller)
            val error = SessionError.Runtime(RuntimeProblem.TIMEOUT)
            world.sessions.failNext(error)

            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            world.projects.calls.shouldBeEmpty()
            controller.state.value.projects shouldBe listOf(project, other)
            controller.state.value.dialog shouldBe Dialog.Message(UiText("remove.runtimeFailed"))
            world.logger
                .entriesAt(LogLevel.WARN)
                .single()
                .fields["reason"] shouldBe StopMessages.category(error)
        }

    @Test
    fun `keep sessions removes without any session command`() =
        runTest {
            val world = world(working, blocked)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }

            world.sessions.calls.shouldBeEmpty()
            world.projects.calls shouldBe listOf("remove project0001")
            world.sessions.snapshot.value
                .countsFor(project.id) shouldBe SessionCounts(1, 1)
        }

    @Test
    fun `a second stop and remove before the first runs is ignored`() =
        runTest {
            val world = world(working, blocked)
            val controller = start(world)
            requestRemove(controller)

            act {
                controller.onEvent(SidebarEvent.StopAndRemove)
                controller.onEvent(SidebarEvent.StopAndRemove)
            }

            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.projects.calls shouldBe listOf("remove project0001")
        }

    @Test
    fun `logs hold ids, counts and categories only`() =
        runTest {
            val secret = "secret-user"
            val dir = Path.of("/Users/$secret/projects/$secret-name")
            val own = aProject(id = "project0003", dir = dir, displayName = "$secret-name")
            val session = WorldSession(SessionId("working"), dir, name = "$secret-session")
            val world =
                ControllerWorld(
                    projects = FakeProjectsApi(initial = listOf(own)),
                    running = listOf(session),
                )
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(own.id)) }
            act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }
            world.sessions.failStop(session.id)
            act { controller.onEvent(SidebarEvent.StopAndRemove) }
            act { controller.onEvent(SidebarEvent.Dismiss) }

            world.logger.entries.shouldNotBeEmpty()
            world.logger.entries.forEach { entry ->
                withClue(entry) {
                    entry.message shouldNotContain secret
                    entry.fields.values.forEach { it.toString() shouldNotContain secret }
                    entry.cause shouldBe null
                }
            }
            world.logger.entries
                .map { it.fields["projectId"] }
                .shouldContain("project0003")
        }
}
