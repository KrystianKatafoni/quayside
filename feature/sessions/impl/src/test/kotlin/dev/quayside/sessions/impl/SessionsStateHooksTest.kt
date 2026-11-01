@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.A_PROJECT_DIR
import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Hooks between two listings (AM-603 plan, "SessionsState"): immediate, confirmed or corrected by polling. */
class SessionsStateHooksTest {
    private val clock = TestClock()
    private val projects = FakeProjectsApi(listOf(Project(ProjectId("p"), A_PROJECT_DIR, "p")))
    private val session = aRuntimeSession()
    private val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))
    private val state = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL, hookLead = 5.seconds)

    private fun needsInput(question: String? = "Which file?") =
        AgentEvent.NeedsInput(anEventMeta(conversationId = aConversationId(), at = clock.now()), question)

    private fun current() =
        state.snapshot.value.sessions
            .single()

    @Test
    fun `a hook before the first listing changes nothing`() =
        runTest {
            state.onHook(needsInput())

            state.snapshot.value shouldBe SessionsSnapshot.Initial
        }

    @Test
    fun `a hook shows the waiting state at once without a listing`() =
        runTest {
            state.refresh()
            val listedAt = state.snapshot.value.status
            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.events.collect { events += it } }
            clock.advanceBy(1.seconds)

            state.onHook(needsInput())
            runCurrent()

            current().state shouldBe SessionState.AwaitingInput("Which file?")
            state.snapshot.value.status shouldBe listedAt
            events
                .single()
                .shouldBeInstanceOf<SessionEvent.BecameBlocked>()
                .session.state shouldBe
                SessionState.AwaitingInput("Which file?")
        }

    @Test
    fun `a listing that still says working does not undo the hook within the lead`() =
        runTest {
            state.refresh()
            clock.advanceBy(1.seconds)
            state.onHook(needsInput())
            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.events.collect { events += it } }

            clock.advanceBy(2.seconds)
            state.refresh()
            runCurrent()

            current().state shouldBe SessionState.AwaitingInput("Which file?")
            events.shouldBeEmpty()
        }

    @Test
    fun `polling confirming the hook adds no second event`() =
        runTest {
            state.refresh()
            clock.advanceBy(1.seconds)
            state.onHook(needsInput())
            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.events.collect { events += it } }

            runtime.setPhase(session.id, RuntimePhase.BLOCKED, WaitingFor.Input)
            clock.advanceBy(2.seconds)
            state.refresh()
            runCurrent()

            current().state shouldBe SessionState.AwaitingInput("Which file?")
            events.shouldBeEmpty()
        }

    @Test
    fun `after the lead polling wins and a lost hook is corrected`() =
        runTest {
            state.refresh()
            clock.advanceBy(1.seconds)
            state.onHook(needsInput())

            clock.advanceBy(10.seconds)
            state.refresh()

            current().state shouldBe SessionState.Working
        }

    @Test
    fun `a hook of an unknown conversation or a session end changes nothing`() =
        runTest {
            state.refresh()
            val before = state.snapshot.value
            val other = anEventMeta(conversationId = ConversationId("nobody"), at = clock.now())

            state.onHook(AgentEvent.NeedsInput(other, "Q?"))
            state.onHook(AgentEvent.SessionEnded(anEventMeta(at = clock.now()), "exit"))

            state.snapshot.value shouldBe before
        }

    @Test
    fun `a hook while the listing is stale keeps the stale status`() =
        runTest {
            state.refresh()
            runtime.failNext(RuntimeError.Timeout)
            clock.advanceBy(2.seconds)
            state.refresh()
            val stale = state.snapshot.value.status
            stale.shouldBeInstanceOf<SnapshotStatus.Stale>()

            state.onHook(needsInput())

            state.snapshot.value.status shouldBe stale
            current().state shouldBe SessionState.AwaitingInput("Which file?")
        }
}
