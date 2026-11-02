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
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.working
import dev.quayside.summaries.api.FakeSummarizer
import dev.quayside.summaries.api.SummariesApi
import dev.quayside.summaries.api.SummaryError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
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

    private val summarizer =
        FakeSummarizer(mapOf(MESSAGE to Outcome.Ok(SUMMARY), BLANK to Outcome.Err(SummaryError.Empty)))
    private val summarized =
        SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL, hookLead = 5.seconds, summarizer = summarizer)

    private fun turnEnded(
        message: String?,
        conversation: ConversationId = aConversationId(),
    ) = AgentEvent.TurnEnded(anEventMeta(conversationId = conversation, at = clock.now()), message)

    private fun summaryOf(from: SessionsState) =
        from.snapshot.value.sessions
            .single()
            .summary

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

    @Test
    fun `turn ended with a message sets the summary`() =
        runTest {
            summarized.refresh()

            summarized.onHook(turnEnded(MESSAGE))

            summaryOf(summarized) shouldBe SUMMARY
        }

    @Test
    fun `turn finished event carries the summary`() =
        runTest {
            summarized.refresh()
            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { summarized.events.collect { events += it } }

            summarized.onHook(turnEnded(MESSAGE))
            runCurrent()

            events
                .single()
                .shouldBeInstanceOf<SessionEvent.TurnFinished>()
                .session.summary shouldBe SUMMARY
        }

    @Test
    fun `empty summary keeps the previous one`() =
        runTest {
            summarized.refresh()
            summarized.onHook(turnEnded(MESSAGE))

            summarized.onHook(turnEnded(BLANK))

            summaryOf(summarized) shouldBe SUMMARY
        }

    @Test
    fun `turn ended without a message keeps the summary`() =
        runTest {
            summarized.refresh()
            summarized.onHook(turnEnded(MESSAGE))

            summarized.onHook(turnEnded(null))
            summarized.onHook(turnEnded(""))

            summaryOf(summarized) shouldBe SUMMARY
            summarizer.calls shouldBe listOf(MESSAGE)
        }

    @Test
    fun `turn ended of an unlisted conversation is not summarized`() =
        runTest {
            summarized.refresh()

            summarized.onHook(turnEnded(MESSAGE, ConversationId("nobody")))

            summaryOf(summarized) shouldBe null
            summarizer.calls.shouldBeEmpty()
        }

    @Test
    fun `turn ended before the first listing is not summarized`() =
        runTest {
            summarized.onHook(turnEnded(MESSAGE))
            summarized.refresh()

            summaryOf(summarized) shouldBe null
            summarizer.calls.shouldBeEmpty()
        }

    @Test
    fun `summary stays after polling overrules the hook`() =
        runTest {
            summarized.refresh()
            summarized.onHook(turnEnded(MESSAGE))

            clock.advanceBy(10.seconds)
            summarized.refresh()

            summarized.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Working
            summaryOf(summarized) shouldBe SUMMARY
        }

    @Test
    fun `summary of a conversation that is no longer listed is dropped`() =
        runTest {
            summarized.refresh()
            summarized.onHook(turnEnded(MESSAGE))
            runtime.remove(session.id)
            summarized.refresh()
            summarized.snapshot.value.sessions
                .shouldBeEmpty()

            runtime.hasSession(session)
            summarized.refresh()

            summaryOf(summarized) shouldBe null
        }

    @Test
    fun `no summaries module means no summary`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()

            world.hooks.emit(turnEnded(MESSAGE))
            runCurrent()

            world.session(S1).summary shouldBe null
        }

    @Test
    fun `summary never reaches the log`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), summaries = SummariesApi(summarizer))
            runCurrent()

            world.hooks.emit(turnEnded(MESSAGE))
            runCurrent()

            world.session(S1).summary shouldBe SUMMARY
            world.logger.entries.forEach { entry ->
                val text = "${entry.message} ${entry.fields} ${entry.cause}"
                text shouldNotContain MESSAGE
                text shouldNotContain SUMMARY
            }
        }

    private companion object {
        const val MESSAGE = "Confidential last message of the turn."
        const val SUMMARY = "Confidential short summary."
        const val BLANK = "only code"
    }
}
