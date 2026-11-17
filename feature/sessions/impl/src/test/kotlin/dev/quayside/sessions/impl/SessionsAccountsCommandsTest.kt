@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Commands of `sessionsModule` over many accounts go to the runtime of the account (AM-1003 plan). */
class SessionsAccountsCommandsTest {
    private val w1 = "bbbb0001"
    private val w2 = "bbbb0002"
    private val w3 = "bbbb0003"

    private fun spec(account: AccountId) = NewSessionSpec(prompt = "Fix the login bug", account = account)

    @Test
    fun `stop respawn remove and recentOutput call only the runtime of the session account`() =
        runTest {
            val stopped = working(w2).copy(phase = RuntimePhase.STOPPED)
            val done = working(w3).copy(phase = RuntimePhase.DONE)
            val world = accountsWorld(listOf(working(S1)), listOf(working(w1), stopped, done))
            runCurrent()

            world.api.commands.stop(SessionId(w1))
            world.api.commands.respawn(SessionId(w2))
            world.api.commands.remove(SessionId(w3))
            world.api.monitor.recentOutput(SessionId(w1))

            world.commandCalls(world.work) shouldBe listOf("stop $w1", "respawn $w2", "remove $w3", "recentOutput $w1")
            world.commandCalls(world.default) shouldBe emptyList()
        }

    @Test
    fun `commands on a default account session do not call the other runtime`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(w1)))
            runCurrent()

            world.api.commands.stop(SessionId(S1))
            world.api.monitor.recentOutput(SessionId(S1))

            world.commandCalls(world.default) shouldBe listOf("stop $S1", "recentOutput $S1")
            world.commandCalls(world.work) shouldBe emptyList()
        }

    @Test
    fun `stopAllIn stops active sessions on every account`() =
        runTest {
            val done = working(w2).copy(phase = RuntimePhase.DONE)
            val world = accountsWorld(listOf(working(S1)), listOf(working(w1), done))
            runCurrent()

            world.api.commands.stopAllIn(P1) shouldBe Outcome.Ok(2)

            world.commandCalls(world.default) shouldBe listOf("stop $S1")
            world.commandCalls(world.work) shouldBe listOf("stop $w1")
            world.session(w1).state shouldBe SessionState.Stopped
            world.session(S1).state shouldBe SessionState.Stopped
        }

    @Test
    fun `start on account work calls only the work runtime and shows the session with that account`() =
        runTest {
            val world = accountsWorld()
            runCurrent()

            val id =
                world.api.commands
                    .start(P1, spec(WORK))
                    .getOrNull()
                    .shouldNotBeNull()

            world.commandCalls(world.work) shouldBe listOf("start")
            world.commandCalls(world.default) shouldBe emptyList()
            world.session(id.value).account shouldBe WORK
        }

    @Test
    fun `start without an account goes to the default runtime`() =
        runTest {
            val world = accountsWorld()
            runCurrent()

            val id =
                world.api.commands
                    .start(P1, NewSessionSpec(prompt = "Fix it"))
                    .getOrNull()
                    .shouldNotBeNull()

            world.commandCalls(world.default) shouldBe listOf("start")
            world.commandCalls(world.work) shouldBe emptyList()
            world.session(id.value).account shouldBe AccountId.DEFAULT
        }

    @Test
    fun `start on an account without a runtime is COMMAND_FAILED and calls no runtime`() =
        runTest {
            val world = accountsWorld()
            runCurrent()

            world.api.commands.start(P1, spec(AccountId("gone"))) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            world.commandCalls(world.default) shouldBe emptyList()
            world.commandCalls(world.work) shouldBe emptyList()
            world.sessions shouldBe emptyList()
        }

    @Test
    fun `start on an account that left the map is COMMAND_FAILED`() =
        runTest {
            val world = accountsWorld()
            runCurrent()
            world.extra.value = emptyMap()
            runCurrent()

            world.api.commands.start(P1, spec(WORK)) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            world.commandCalls(world.work) shouldBe emptyList()
        }

    @Test
    fun `disk sessions and their resume belong to the default account`() =
        runTest {
            val world = accountsWorld(history = FakeSessionHistory(listOf(conversation(1))))
            runCurrent()
            val disk = world.sessions.single { it.origin == SessionOrigin.DISK }
            disk.account shouldBe AccountId.DEFAULT

            world.api.commands
                .resume(disk.id)
                .getOrNull()
                .shouldNotBeNull()

            world.commandCalls(world.default) shouldBe listOf("resume ${uuidOf(1)}")
            world.commandCalls(world.work) shouldBe emptyList()
            world.sessions.single { it.origin == SessionOrigin.SUPERVISOR }.account shouldBe AccountId.DEFAULT
        }

    @Test
    fun `a hook event updates a session on an extra account`() =
        runTest {
            val conversation = aConversationId()
            val onWork = working(w1).copy(conversationId = conversation)
            val world = accountsWorld(workSessions = listOf(onWork))
            runCurrent()

            world.hooks.emit(AgentEvent.NeedsInput(anEventMeta(conversationId = conversation, at = T0), "Which file?"))
            runCurrent()

            world.session(w1).state shouldBe SessionState.AwaitingInput("Which file?")
            world.session(w1).account shouldBe WORK
        }

    @Test
    fun `an unknown session is UnknownSession and no runtime is told to stop it`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(w1)))
            runCurrent()
            val unknown = SessionId("ffffffff")

            world.api.commands.stop(unknown) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))
            world.api.monitor.recentOutput(unknown) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))

            world.commandCalls(world.default).none { it.startsWith("stop") } shouldBe true
            world.commandCalls(world.work).none { it.startsWith("stop") } shouldBe true
        }
}
