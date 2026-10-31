@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * StopSelected and RespawnSelected and the public `actions` state (FR-S4, AM-410). Sessions start at T0 + their
 * index, so rows are newest first. Time never moves: the tick is one hour and `runCurrent` runs only what is due.
 */
internal class SessionActionsControllerTest {
    private val project = aProject(displayName = "Quayside")
    private val log = RecordingLogger()

    private fun world(vararg sessions: WorldSession): FakeSessions =
        FakeSessions(SessionsWorld(listOf(WorldProject(project.id, project.dir)), sessions.toList()), TestClock())

    private fun session(
        id: String,
        index: Long,
        state: SessionState = SessionState.Working,
    ): WorldSession =
        WorldSession(
            id = SessionId(id),
            cwd = A_PROJECT_DIR,
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

    private fun MissionControlController.page(): Screen.Project =
        state.value.screen.shouldBeInstanceOf<Screen.Project>()

    private fun MissionControlController.details(): SessionDetails? = page().details

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

    private fun stateOf(
        fake: FakeSessions,
        id: String,
    ): SessionState =
        fake.snapshot.value.sessions
            .single { it.id.value == id }
            .state

    @Test
    fun `stop intent stops the selected working session`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.StopSelected)

            fake.calls shouldBe listOf("stop a")
            stateOf(fake, "a") shouldBe SessionState.Stopped
            controller.details()?.actions shouldBe setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
        }

    @Test
    fun `stop intent on a finished session calls nothing`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.StopSelected)

            fake.calls.shouldBeEmpty()
            controller.details()?.failure.shouldBeNull()
        }

    @Test
    fun `respawn intent respawns a stopped session`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Stopped))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.RespawnSelected)

            fake.calls shouldBe listOf("respawn a")
            stateOf(fake, "a") shouldBe SessionState.Working
            controller.details()?.actions shouldBe setOf(SessionAction.STOP)
        }

    @Test
    fun `respawn intent on a working session calls nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.RespawnSelected)

            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `intents without a selection call nothing`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2, SessionState.Done))
            val controller = start(fake)

            send(controller, MissionControlIntent.StopSelected)
            send(controller, MissionControlIntent.RespawnSelected)

            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `a session hidden by the filter is not stopped`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2, SessionState.Done))
            val controller = start(fake)
            select(controller, "a")
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()

            send(controller, MissionControlIntent.StopSelected)

            fake.calls.shouldBeEmpty()
            stateOf(fake, "a") shouldBe SessionState.Working
        }

    @Test
    fun `next session then stop stops the new selection`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2), session("c", 3))
            val controller = start(fake)
            select(controller, "c")

            // No runCurrent between the intents: the target is read fresh, not from the state of the screen.
            controller.onIntent(MissionControlIntent.NextSession)
            controller.onIntent(MissionControlIntent.StopSelected)
            runCurrent()

            fake.calls shouldBe listOf("stop b")
        }

    @Test
    fun `a second action while one runs is ignored`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            select(controller, "a")

            controller.onIntent(MissionControlIntent.StopSelected)
            controller.onIntent(MissionControlIntent.StopSelected)
            runCurrent()
            select(controller, "b")
            send(controller, MissionControlIntent.StopSelected)
            send(controller, MissionControlIntent.RespawnSelected)
            gated.started shouldBe listOf("stop a")

            gated.gate.complete(Unit)
            runCurrent()

            fake.calls shouldBe listOf("stop a")
            stateOf(fake, "a") shouldBe SessionState.Stopped
            stateOf(fake, "b") shouldBe SessionState.Working
        }

    @Test
    fun `actions are off while an action runs`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2, SessionState.Done))
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            select(controller, "a")
            controller.actions.value shouldBe
                SessionActionsState(canStop = true, canRespawn = false, canPreview = true, canAttach = true)

            send(controller, MissionControlIntent.StopSelected)

            controller.actions.value shouldBe
                SessionActionsState(canStop = false, canRespawn = false, canPreview = true, canAttach = true)
            controller.details()?.busy shouldBe SessionAction.STOP
            controller.details()?.actions shouldBe emptySet()
            // The other session is off too while the action runs.
            select(controller, "b")
            controller.actions.value shouldBe
                SessionActionsState(canStop = false, canRespawn = false, canPreview = true, canAttach = true)
            controller.details()?.busy.shouldBeNull()

            gated.gate.complete(Unit)
            runCurrent()

            controller.actions.value shouldBe
                SessionActionsState(
                    canStop = false,
                    canRespawn = true,
                    canRemove = true,
                    canPreview = true,
                    canAttach = true,
                )
        }

    @Test
    @Suppress("LongMethod")
    fun `actions follow the selected session`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2, SessionState.Done))
            val controller = start(fake)
            controller.actions.value shouldBe SessionActionsState(canStop = false, canRespawn = false)

            select(controller, "a")
            controller.actions.value shouldBe
                SessionActionsState(canStop = true, canRespawn = false, canPreview = true, canAttach = true)

            select(controller, "b")
            controller.actions.value shouldBe
                SessionActionsState(
                    canStop = false,
                    canRespawn = true,
                    canRemove = true,
                    canPreview = true,
                    canAttach = true,
                )

            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.ACTIVE))
            runCurrent()
            controller.actions.value shouldBe SessionActionsState(canStop = false, canRespawn = false)

            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.ALL))
            fake.setState(SessionId("b"), SessionState.AwaitingPermission("Bash"))
            runCurrent()
            controller.actions.value shouldBe
                SessionActionsState(
                    canStop = true,
                    canRespawn = false,
                    canPreview = true,
                    canAttach = true,
                    canReply = true,
                )
        }

    @Test
    fun `a failed stop shows its reason only in that session`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            fake.failStop(SessionId("a"))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.StopSelected)

            controller.details()?.failure shouldBe
                Failure(SessionId("a"), SessionAction.STOP, UiText("action.reason.runtime.COMMAND_FAILED"))
            controller.details()?.busy.shouldBeNull()
            // The session still runs, so Stop stays available.
            controller.details()?.actions shouldBe setOf(SessionAction.STOP)

            select(controller, "b")
            controller.details()?.failure.shouldBeNull()
        }

    @Test
    fun `failure is cleared by the next action`() =
        runTest {
            val fake = world(session("a", 1))
            fake.failNext(SessionError.UnknownSession(SessionId("a")))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.StopSelected)
            controller.details()?.failure?.reason shouldBe UiText("action.reason.unknownSession")

            send(controller, MissionControlIntent.StopSelected)

            controller.details()?.failure.shouldBeNull()
            stateOf(fake, "a") shouldBe SessionState.Stopped
        }

    @Test
    fun `failure is cleared when another session is selected`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            fake.failStop(SessionId("a"))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.StopSelected)
            controller.details()?.failure.shouldBeInstanceOf<Failure>()

            select(controller, "b")
            select(controller, "a")

            controller.details()?.failure.shouldBeNull()
        }

    @Test
    fun `logs hold the session id and category only`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            fake.failStop(SessionId("b"))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.StopSelected)
            send(controller, MissionControlIntent.RespawnSelected)
            select(controller, "b")
            send(controller, MissionControlIntent.StopSelected)

            log.entries.map { it.level to it.message } shouldBe
                listOf(
                    LogLevel.INFO to "session stopped",
                    LogLevel.INFO to "session respawned",
                    LogLevel.WARN to "session stop failed",
                )
            log.entries.map { it.fields } shouldBe
                listOf(
                    mapOf("sessionId" to "a"),
                    mapOf("sessionId" to "a"),
                    mapOf("sessionId" to "b", "reason" to "runtime.COMMAND_FAILED"),
                )
            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain "name-"
            logged shouldNotContain A_PROJECT_DIR.toString()
        }
}
