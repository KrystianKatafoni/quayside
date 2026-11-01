package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

/** What hook details do in the reducer (AM-603 plan, "Reducer"). */
class SessionStateReducerHooksTest {
    private val now: Instant = T0.plusSeconds(100)
    private val hookAt: Instant = T0.plusSeconds(90)

    private fun reduce(
        previous: SessionsSnapshot,
        session: RuntimeSession,
        detail: HookDetail?,
    ) = SessionStateReducer.reduce(
        previous,
        Outcome.Ok(listOf(session)),
        emptyMap(),
        now,
        listOfNotNull(detail?.let { session.id to it }).toMap(),
    )

    private fun fresh(vararg state: SessionState) =
        SessionsSnapshot(
            state.map { aSession(state = it, lastActivityAt = T0) },
            SnapshotStatus.Fresh(T0.plusSeconds(98)),
        )

    private val blockedInput = aRuntimeSession(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input)
    private val blockedPermission = aRuntimeSession(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Permission)

    @Test
    fun `the question goes into the waiting state and the blocked event carries it`() {
        val reduction = reduce(fresh(SessionState.Working), blockedInput, HookDetail(hookAt, "Which file?", null))

        reduction.snapshot.sessions
            .single()
            .state shouldBe SessionState.AwaitingInput("Which file?")
        reduction.events
            .single()
            .shouldBeInstanceOf<SessionEvent.BecameBlocked>()
            .session.state shouldBe
            SessionState.AwaitingInput("Which file?")
    }

    @Test
    fun `the tool goes into the permission state`() {
        val reduction = reduce(fresh(SessionState.Working), blockedPermission, HookDetail(hookAt, null, "Bash"))

        reduction.snapshot.sessions
            .single()
            .state shouldBe SessionState.AwaitingPermission("Bash")
    }

    @Test
    fun `details of another kind are ignored`() {
        val working = aRuntimeSession()

        val reduction = reduce(fresh(SessionState.Working), working, HookDetail(hookAt, "Q?", "Bash"))

        reduction.snapshot.sessions
            .single()
            .state shouldBe SessionState.Working
    }

    @Test
    fun `last activity is never before the hook`() {
        val reduction =
            reduce(fresh(SessionState.Done), aRuntimeSession(phase = RuntimePhase.DONE), HookDetail(hookAt, null, null))

        reduction.snapshot.sessions
            .single()
            .lastActivityAt shouldBe hookAt
    }

    @Test
    fun `a question that arrives after the blocked event is no new event`() {
        val previous = fresh(SessionState.AwaitingInput(question = null))

        val reduction = reduce(previous, blockedInput, HookDetail(hookAt, "Which file?", null))

        reduction.events.shouldBeEmpty()
        reduction.snapshot.sessions
            .single()
            .state shouldBe SessionState.AwaitingInput("Which file?")
    }

    @Test
    fun `the first listing with details stays a baseline without events`() {
        val reduction = reduce(SessionsSnapshot.Initial, blockedInput, HookDetail(hookAt, "Q?", null))

        reduction.events.shouldBeEmpty()
        reduction.snapshot.sessions
            .single()
            .lastActivityAt shouldBe hookAt
    }

    @Test
    fun `no details leave the reduction as before`() {
        val reduction = reduce(fresh(SessionState.Working), blockedInput, null)

        reduction.snapshot.sessions
            .single()
            .state shouldBe SessionState.AwaitingInput(null)
    }
}
