@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.A_PROJECT_DIR
import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.FakeAgentRuntime
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.Project
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.SessionEvent
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** The single owner of the listing, the published snapshot and events (AM-403/AM-404 plans, `SessionsState`). */
class SessionsStateTest {
    private val clock = TestClock()
    private val project = ProjectId("agent-manager")
    private val projects = FakeProjectsApi(listOf(Project(project, A_PROJECT_DIR, "agent-manager")))

    private fun stateOver(
        runtime: AgentRuntime,
        clock: Clock = this.clock,
    ): SessionsState = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL)

    private fun runtimeWith(vararg sessions: RuntimeSession): FakeAgentRuntime =
        FakeAgentRuntime(RuntimeWorld(sessions = sessions.toList()))

    @Test
    fun `starts with the initial snapshot`() {
        stateOver(runtimeWith()).snapshot.value shouldBe SessionsSnapshot.Initial
    }

    @Test
    fun `publishes the snapshot before the events`() =
        runTest {
            val runtime = runtimeWith(aRuntimeSession())
            val state = stateOver(runtime)
            state.refresh()
            val seen =
                mutableListOf<Pair<SessionEvent, SessionsSnapshot>>().also { seen ->
                    // Unconfined: the collector runs inside emit, so it sees the snapshot of that moment.
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        state.events.collect { seen += it to state.snapshot.value }
                    }
                }

            clock.advanceBy(2.seconds)
            runtime.setPhase(aRuntimeSession().id, RuntimePhase.DONE)
            val refreshed = state.refresh()
            runCurrent()

            val (event, snapshotAtEvent) = seen.single()
            event.shouldBeInstanceOf<SessionEvent.TurnFinished>()
            snapshotAtEvent.sessions shouldContain event.session
            refreshed shouldBe Outcome.Ok(state.snapshot.value)
        }

    @Test
    fun `failed listing publishes no events`() =
        runTest {
            val runtime = runtimeWith(aRuntimeSession())
            val state = stateOver(runtime)
            state.refresh()
            val events = record(state.events)

            clock.advanceBy(2.seconds)
            runtime.failNext(RuntimeError.Timeout)
            val refreshed = state.refresh()
            runCurrent()

            refreshed shouldBe Outcome.Err(RuntimeError.Timeout)
            events.shouldBeEmpty()
            state.snapshot.value.status shouldBe
                SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)
            state.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Working
        }

    @Test
    fun `events are not replayed to late collectors`() =
        runTest {
            val runtime = runtimeWith(aRuntimeSession())
            val state = stateOver(runtime)
            state.refresh()
            clock.advanceBy(2.seconds)
            runtime.setPhase(aRuntimeSession().id, RuntimePhase.FAILED)
            state.refresh()

            val late = record(state.events)
            runCurrent()

            late.shouldBeEmpty()
            state.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Failed
        }

    @Test
    fun `every event of one listing reaches a collector`() =
        runTest {
            val ids = (1..10).map { SessionId("aaaa%04d".format(Locale.ROOT, it)) }
            val runtime = runtimeWith(*ids.map { aRuntimeSession(id = it) }.toTypedArray())
            val state = stateOver(runtime)
            state.refresh()
            val events = record(state.events)

            clock.advanceBy(2.seconds)
            ids.forEach { runtime.setPhase(it, RuntimePhase.DONE) }
            state.refresh()
            runCurrent()

            events shouldHaveSize ids.size
        }

    @Test
    fun `fresh time comes from the clock`() =
        runTest {
            val state = stateOver(runtimeWith(aRuntimeSession()))
            clock.advanceBy(5.seconds)

            state.refresh()

            state.snapshot.value.status shouldBe SnapshotStatus.Fresh(T0.plusSeconds(5))
        }

    @Test
    fun `refresh attributes sessions to the registered projects`() =
        runTest {
            val outside = aRuntimeSession(id = SessionId("aaaa0002"), cwd = MonitorFixtures.OUTSIDE_DIR)
            val state = stateOver(runtimeWith(aRuntimeSession(), outside))

            state.refresh()

            state.snapshot.value.sessions
                .map { it.id to it.project } shouldBe listOf(aRuntimeSession().id to project, outside.id to null)
        }

    @Test
    fun `expiry is the clock time plus the overlay ttl`() {
        val state = stateOver(runtimeWith())
        clock.advanceBy(3.seconds)

        state.expiry() shouldBe T0.plusSeconds(3 + COMMAND_OVERLAY_TTL.inWholeSeconds)
    }

    @Test
    fun `older listing never overwrites a newer one`() =
        runTest {
            // The first listing reads the supervisor at 0 s and returns at 2 s; the second one is
            // asked for at 1 s, after the session finished. It must not be overtaken by the first.
            val fake = runtimeWith(aRuntimeSession(id = SessionId(S1)))
            val state = stateOver(DelayedListingRuntime(fake, listOf(2.seconds)), virtualClock())
            val published = recordUnconfined(state)

            val first = launch { state.refresh() }
            runCurrent()
            advance(1.seconds)
            fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            val second = launch { state.refresh() }
            advance(2.seconds)

            first.isCompleted shouldBe true
            second.isCompleted shouldBe true
            published.mapNotNull { snapshot -> snapshot.sessions.singleOrNull()?.state } shouldBe
                listOf(SessionState.Working, SessionState.Done)
            state.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Done
        }

    @Test
    fun `failed refresh keeps pending expectations`() =
        runTest {
            val runtime = runtimeWith(aRuntimeSession(id = SessionId(S1)))
            val state = stateOver(runtime)
            state.refresh()
            val stop = Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)

            clock.advanceBy(1.seconds)
            runtime.failNext(RuntimeError.Timeout)
            state.refresh(listOf(stop)) shouldBe Outcome.Err(RuntimeError.Timeout)
            state.snapshot.value.status shouldBe
                SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)

            // The supervisor still lists the session as working: the kept expectation shows it stopped.
            clock.advanceBy(1.seconds)
            state.refresh()

            state.snapshot.value.status shouldBe SnapshotStatus.Fresh(T0.plusSeconds(2))
            state.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Stopped
        }

    @Test
    fun `confirmed expectation emits no event`() =
        runTest {
            val runtime = runtimeWith(aRuntimeSession(id = SessionId(S1), phase = RuntimePhase.DONE))
            val state = stateOver(runtime)
            state.refresh()
            val events = recordEventsUnconfined(state)
            val respawn = Expected.Moves(SessionId(S1), RuntimePhase.DONE, RuntimePhase.WORKING, UNTIL)

            clock.advanceBy(1.seconds)
            state.refresh(listOf(respawn))
            state.snapshot.value.sessions
                .single()
                .let { it.state to it.lastActivityAt } shouldBe (SessionState.Working to T0.plusSeconds(1))

            clock.advanceBy(1.seconds)
            runtime.respawn(SessionId(S1))
            state.refresh()

            events.shouldBeEmpty()
            state.snapshot.value.sessions
                .single()
                .let { it.state to it.lastActivityAt } shouldBe (SessionState.Working to T0.plusSeconds(1))

            // The confirmed expectation is gone: the next DONE is the agent's own and is reported.
            clock.advanceBy(1.seconds)
            runtime.setPhase(SessionId(S1), RuntimePhase.DONE)
            state.refresh()

            state.snapshot.value.sessions
                .single()
                .state shouldBe SessionState.Done
            events.map { it::class } shouldBe listOf(SessionEvent.TurnFinished::class)
        }

    /** Every published snapshot, seen inside the update (unconfined), so none is conflated away. */
    private fun TestScope.recordUnconfined(state: SessionsState): List<SessionsSnapshot> {
        val seen = mutableListOf<SessionsSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.snapshot.collect { seen += it } }
        return seen
    }

    private fun TestScope.recordEventsUnconfined(state: SessionsState): List<SessionEvent> {
        val seen = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.events.collect { seen += it } }
        return seen
    }

    private companion object {
        const val S1 = "aaaa0001"
        val UNTIL: java.time.Instant = T0.plusSeconds(10)
    }
}
