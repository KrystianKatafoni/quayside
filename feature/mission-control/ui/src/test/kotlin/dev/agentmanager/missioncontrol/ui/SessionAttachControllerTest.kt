@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionCommands
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * `attachTarget()`, `SessionActionsState.canAttach` and `canReply` of the controller (FR-K2, AM-505, AM-506).
 * Sessions start at T0 + their index, so rows are newest first. Time never moves: the tick is one hour.
 */
internal class SessionAttachControllerTest {
    private val project = aProject(id = "project0001", displayName = "Agent Manager")
    private val log = RecordingLogger()

    private fun world(vararg sessions: WorldSession): FakeSessions =
        FakeSessions(SessionsWorld(listOf(WorldProject(project.id, project.dir)), sessions.toList()), TestClock())

    private fun session(
        id: String,
        index: Long,
        state: SessionState = SessionState.Working,
        cwd: Path = A_PROJECT_DIR,
    ): WorldSession =
        WorldSession(
            id = SessionId(id),
            cwd = cwd,
            state = state,
            name = "name-$id",
            startedAt = T0.plusSeconds(index),
        )

    private fun TestScope.start(
        fake: FakeSessions,
        commands: SessionCommands = fake,
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = commands),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            TestClock(),
            backgroundScope,
            log,
            1.hours,
        ).also { runCurrent() }

    private fun TestScope.select(
        controller: MissionControlController,
        id: String,
    ) {
        controller.onEvent(MissionControlEvent.SelectSession(SessionId(id)))
        runCurrent()
    }

    private fun TestScope.send(
        controller: MissionControlController,
        intent: MissionControlIntent,
    ) {
        controller.onIntent(intent)
        runCurrent()
    }

    @Test
    fun `attach target is the selected visible session with its project`() =
        runTest {
            val controller = start(world(session("a", 1), session("b", 2)))
            select(controller, "a")

            controller.attachTarget() shouldBe AttachTarget(SessionId("a"), "name-a", project)
            log.entries.shouldBeEmpty()
        }

    @Test
    fun `attach target is null without a selection`() =
        runTest {
            val controller = start(world(session("a", 1)))

            controller.attachTarget().shouldBeNull()
        }

    @Test
    fun `attach target is null for a session outside the registered projects`() =
        runTest {
            val outside = session("a", 1, SessionState.AwaitingInput(null), cwd = Path.of("/Users/test/elsewhere"))
            val controller = start(world(outside))
            send(controller, MissionControlIntent.ShowView(GlobalView.WAITING))
            select(controller, "a")

            controller.attachTarget().shouldBeNull()
            controller.actions.value.canAttach shouldBe false
        }

    @Test
    fun `attach target is null while the remove dialog is open`() =
        runTest {
            val controller = start(world(session("a", 1, SessionState.Done)))
            select(controller, "a")
            controller.attachTarget() shouldBe AttachTarget(SessionId("a"), "name-a", project)

            send(controller, MissionControlIntent.RemoveSelected)

            controller.attachTarget().shouldBeNull()
        }

    @Test
    fun `attach target follows j read in the same frame`() =
        runTest {
            val controller = start(world(session("a", 1), session("b", 2), session("c", 3)))
            select(controller, "c")

            // No runCurrent after j: the target is read fresh, not from the lagging state.
            controller.onIntent(MissionControlIntent.NextSession)

            controller.attachTarget()?.session shouldBe SessionId("b")
        }

    @Test
    fun `attach target stays while a stop runs`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake, GatedCommands(fake))
            select(controller, "a")

            send(controller, MissionControlIntent.StopSelected)

            controller.attachTarget() shouldBe AttachTarget(SessionId("a"), "name-a", project)
            controller.actions.value.canAttach shouldBe true
        }

    @Test
    fun `actions offer attach for a selected session of a project`() =
        runTest {
            val controller = start(world(session("a", 1), session("b", 2, SessionState.Stopped)))
            controller.actions.value.canAttach shouldBe false

            select(controller, "a")
            controller.actions.value.canAttach shouldBe true

            select(controller, "b")
            controller.actions.value.canAttach shouldBe true
        }

    @Test
    fun `actions offer no attach with a dialog open`() =
        runTest {
            val controller = start(world(session("a", 1, SessionState.Done)))
            select(controller, "a")

            send(controller, MissionControlIntent.RemoveSelected)

            controller.actions.value.dialogOpen shouldBe true
            controller.actions.value.canAttach shouldBe false
        }

    @Test
    fun `attach target is waiting for a session awaiting input`() =
        runTest {
            val controller = start(world(session("a", 1, SessionState.AwaitingInput("Continue?"))))
            select(controller, "a")

            controller.attachTarget() shouldBe AttachTarget(SessionId("a"), "name-a", project, waiting = true)
        }

    @Test
    fun `attach target is waiting for a session awaiting permission`() =
        runTest {
            val controller = start(world(session("a", 1, SessionState.AwaitingPermission("Bash"))))
            select(controller, "a")

            controller.attachTarget()?.waiting shouldBe true
        }

    @Test
    fun `attach target is not waiting right after the session starts working`() =
        runTest {
            val fake = world(session("a", 1, SessionState.AwaitingInput(null)))
            val controller = start(fake)
            select(controller, "a")
            controller.attachTarget()?.waiting shouldBe true

            // No runCurrent after the change: the target is read fresh, not from the lagging state.
            fake.setState(SessionId("a"), SessionState.Working)

            controller.attachTarget()?.waiting shouldBe false
        }

    @Test
    fun `actions offer reply for a waiting session of a project`() =
        runTest {
            val controller = start(world(session("a", 1, SessionState.AwaitingInput("Q?"))))
            controller.actions.value.canReply shouldBe false

            select(controller, "a")

            controller.actions.value.canReply shouldBe true
            controller.actions.value.canAttach shouldBe true
        }

    @Test
    fun `actions offer no reply for a working session`() =
        runTest {
            val controller = start(world(session("a", 1), session("b", 2, SessionState.Stopped)))

            select(controller, "a")
            controller.actions.value.canReply shouldBe false
            controller.actions.value.canAttach shouldBe true

            select(controller, "b")
            controller.actions.value.canReply shouldBe false
        }

    @Test
    fun `actions offer no reply with a dialog open`() =
        runTest {
            // Only a finished session can be removed; it starts waiting again while the dialog is open.
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)
            fake.setState(SessionId("a"), SessionState.AwaitingInput("Q?"))
            runCurrent()

            controller.actions.value.dialogOpen shouldBe true
            controller.actions.value.canReply shouldBe false
        }

    @Test
    fun `actions keep reply while a stop runs`() =
        runTest {
            val fake = world(session("a", 1, SessionState.AwaitingInput(null)))
            val controller = start(fake, GatedCommands(fake))
            select(controller, "a")

            send(controller, MissionControlIntent.StopSelected)

            controller.actions.value.canReply shouldBe true
        }
}
