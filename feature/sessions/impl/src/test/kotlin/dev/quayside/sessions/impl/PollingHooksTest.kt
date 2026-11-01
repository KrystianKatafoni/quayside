@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** `sessionsModule` with hook events (AM-603 plan, NFR-2): a hook shows up before the next poll. */
class PollingHooksTest {
    private val needsInput =
        AgentEvent.NeedsInput(anEventMeta(conversationId = aConversationId(), at = T0), "Which file?")

    @Test
    fun `a hook changes the snapshot before the next poll`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()

            advanceTimeBy(500.milliseconds)
            world.hooks.emit(needsInput)
            runCurrent()

            world.stateOf(S1) shouldBe SessionState.AwaitingInput("Which file?")
            world.runtime.listedAtMillis shouldBe listOf(0L)
        }

    @Test
    fun `polling takes over once the supervisor lists the session as blocked`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            world.hooks.emit(needsInput)
            runCurrent()

            world.fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            advanceTimeBy(10.seconds)

            world.stateOf(S1) shouldBe SessionState.Done
        }

    @Test
    fun `a lost hook is no problem, polling shows the state`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()

            world.fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            advanceTimeBy(2.seconds)
            runCurrent()

            world.stateOf(S1) shouldBe SessionState.Done
        }
}
