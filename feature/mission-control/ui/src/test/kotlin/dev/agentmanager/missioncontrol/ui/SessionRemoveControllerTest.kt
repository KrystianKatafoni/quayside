@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionCommands
import dev.agentmanager.sessions.api.SessionError
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
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
 * RemoveSelected, the confirmation dialog and `actions.dialogOpen` (FR-S4, AM-410). Sessions start at T0 + their
 * index, so rows are newest first. Time never moves: the tick is one hour and `runCurrent` runs only what is due.
 */
internal class SessionRemoveControllerTest {
    private val project = aProject(displayName = "Agent Manager")
    private val log = RecordingLogger()

    private fun world(vararg sessions: WorldSession): FakeSessions =
        FakeSessions(SessionsWorld(listOf(WorldProject(project.id, project.dir)), sessions.toList()), TestClock())

    private fun session(
        id: String,
        index: Long,
        state: SessionState = SessionState.Done,
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

    private fun MissionControlController.details(): SessionDetails? =
        state.value.screen
            .shouldBeInstanceOf<Screen.Project>()
            .details

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

    private fun TestScope.event(
        controller: MissionControlController,
        event: MissionControlEvent,
    ) {
        controller.onEvent(event)
        runCurrent()
    }

    private fun ids(fake: FakeSessions): List<String> =
        fake.snapshot.value.sessions
            .map { it.id.value }

    @Test
    fun `remove intent opens the confirmation and calls nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.RemoveSelected)

            controller.state.value.dialog shouldBe SessionDialog.ConfirmRemove(SessionId("a"), "name-a")
            fake.calls.shouldBeEmpty()
            log.entries.shouldBeEmpty()
            ids(fake) shouldBe listOf("a")
        }

    @Test
    fun `remove intent on a working session opens nothing`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Working))
            val controller = start(fake)
            select(controller, "a")

            send(controller, MissionControlIntent.RemoveSelected)

            controller.state.value.dialog
                .shouldBeNull()
            controller.actions.value.dialogOpen shouldBe false
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `remove intent without a selection opens nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)

            send(controller, MissionControlIntent.RemoveSelected)

            controller.state.value.dialog
                .shouldBeNull()
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `confirming removes the session from the dialog`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)

            event(controller, MissionControlEvent.ConfirmRemove)

            fake.calls shouldBe listOf("remove a")
            ids(fake) shouldBe listOf("b")
            controller.state.value.dialog
                .shouldBeNull()
            controller.actions.value.dialogOpen shouldBe false
        }

    @Test
    fun `confirming removes the dialog session even after the selection changed`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)
            select(controller, "b")

            event(controller, MissionControlEvent.ConfirmRemove)

            fake.calls shouldBe listOf("remove a")
            ids(fake) shouldBe listOf("b")
        }

    @Test
    fun `confirming without a dialog calls nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")

            event(controller, MissionControlEvent.ConfirmRemove)

            fake.calls.shouldBeEmpty()
            ids(fake) shouldBe listOf("a")
        }

    @Test
    fun `dismissing closes the dialog and calls nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)

            event(controller, MissionControlEvent.Dismiss)

            controller.state.value.dialog
                .shouldBeNull()
            fake.calls.shouldBeEmpty()
            log.entries.shouldBeEmpty()
            ids(fake) shouldBe listOf("a")
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
    fun `actions report an open dialog and offer nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")
            controller.actions.value shouldBe
                SessionActionsState(
                    canStop = false,
                    canRespawn = true,
                    canRemove = true,
                    canPreview = true,
                    canAttach = true,
                )

            send(controller, MissionControlIntent.RemoveSelected)

            controller.actions.value shouldBe
                SessionActionsState(canStop = false, canRespawn = false, canRemove = false, dialogOpen = true)
            controller.details()?.actions shouldBe emptySet()
        }

    @Test
    fun `intents are ignored while the dialog is open`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)
            val dialog = controller.state.value.dialog

            send(controller, MissionControlIntent.PreviousSession)
            send(controller, MissionControlIntent.NextSession)
            send(controller, MissionControlIntent.StopSelected)
            send(controller, MissionControlIntent.RespawnSelected)
            send(controller, MissionControlIntent.RemoveSelected)

            controller.details()?.id shouldBe SessionId("a")
            controller.state.value.dialog shouldBe dialog
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `a failed remove shows its reason in that session`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)
            // The session was resumed elsewhere while the dialog was open.
            fake.setState(SessionId("a"), SessionState.Working)
            runCurrent()

            event(controller, MissionControlEvent.ConfirmRemove)

            fake.calls shouldBe listOf("remove a")
            controller.state.value.dialog
                .shouldBeNull()
            controller.details()?.failure shouldBe
                Failure(SessionId("a"), SessionAction.REMOVE, UiText("action.reason.sessionActive"))
            controller.details()?.busy.shouldBeNull()
            ids(fake) shouldBe listOf("a", "b")

            select(controller, "b")
            controller.details()?.failure.shouldBeNull()
        }

    @Test
    fun `a failed remove from the runtime keeps the session and offers remove again`() =
        runTest {
            val fake = world(session("a", 1))
            fake.failNext(SessionError.UnknownSession(SessionId("a")))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)

            event(controller, MissionControlEvent.ConfirmRemove)

            controller.details()?.failure?.reason shouldBe UiText("action.reason.unknownSession")
            controller.details()?.actions shouldBe setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
        }

    @Test
    fun `confirming shows the removing state until the command ends`() =
        runTest {
            val fake = world(session("a", 1))
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)

            event(controller, MissionControlEvent.ConfirmRemove)

            gated.started shouldBe listOf("remove a")
            controller.state.value.dialog
                .shouldBeNull()
            controller.details()?.busy shouldBe SessionAction.REMOVE
            controller.details()?.actions shouldBe emptySet()
            controller.actions.value shouldBe
                SessionActionsState(canStop = false, canRespawn = false, canPreview = true, canAttach = true)

            gated.gate.complete(Unit)
            runCurrent()

            ids(fake).shouldBeEmpty()
        }

    @Test
    fun `removed session leaves the details empty`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)

            event(controller, MissionControlEvent.ConfirmRemove)

            controller.details().shouldBeNull()
            controller.state.value.screen
                .shouldBeInstanceOf<Screen.Project>()
                .rows
                .shouldBeEmpty()
            controller.actions.value shouldBe SessionActionsState(canStop = false, canRespawn = false)
        }

    @Test
    fun `logs of remove hold the session id and category only`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, "a")
            send(controller, MissionControlIntent.RemoveSelected)
            event(controller, MissionControlEvent.Dismiss)
            send(controller, MissionControlIntent.RemoveSelected)
            event(controller, MissionControlEvent.ConfirmRemove)
            select(controller, "b")
            send(controller, MissionControlIntent.RemoveSelected)
            fake.setState(SessionId("b"), SessionState.Working)
            runCurrent()
            event(controller, MissionControlEvent.ConfirmRemove)

            log.entries.map { it.level to it.message } shouldBe
                listOf(LogLevel.INFO to "session removed", LogLevel.WARN to "session remove failed")
            log.entries.map { it.fields } shouldBe
                listOf(mapOf("sessionId" to "a"), mapOf("sessionId" to "b", "reason" to "sessionActive"))
            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain "name-"
            logged shouldNotContain A_PROJECT_DIR.toString()
        }
}
