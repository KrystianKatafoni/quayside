package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.A_PROJECT_DIR
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.aSessionId
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionEvent
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.sessions.api.aSession
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.time.Instant

/** Rules of the reducer from the AM-402 plan ("Reguły reducera" 1–5 and the transition table). */
class SessionStateReducerTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("transitions")
    fun `emits events only for transitions`(
        case: String,
        before: SessionState?,
        listed: RuntimeSession?,
        expectedEvent: ((Session) -> SessionEvent)?,
        expectedLastActivity: Instant?,
    ) {
        val previous = fresh(listOfNotNull(before?.let { aSession(state = it, lastActivityAt = EARLIER) }))

        val reduction = reduce(previous, Outcome.Ok(listOfNotNull(listed)))

        withClue(case) {
            reduction.snapshot.sessions.size shouldBe listOfNotNull(listed).size
            val session = reduction.snapshot.sessions.singleOrNull()
            session?.lastActivityAt shouldBe expectedLastActivity
            reduction.events shouldBe listOfNotNull(session?.let { expectedEvent?.invoke(it) })
        }
    }

    @Test
    fun `first listing emits no events`() {
        val listing = ok(blocked("a"), runtime("b", RuntimePhase.DONE), runtime("c", RuntimePhase.FAILED))

        reduce(SessionsSnapshot.Initial, listing).events.shouldBeEmpty()
    }

    @Test
    fun `first listing sets last activity to start time`() {
        val listing = ok(runtime("a", startedAt = T0), blocked("b", startedAt = T0.plusSeconds(60)))

        val sessions = reduce(SessionsSnapshot.Initial, listing).snapshot.sessions

        sessions shouldBe
            listOf(
                aSession(id = "a", startedAt = T0, lastActivityAt = T0),
                aSession(
                    id = "b",
                    state = AWAITING_INPUT,
                    startedAt = T0.plusSeconds(60),
                    lastActivityAt = T0.plusSeconds(60),
                ),
            )
    }

    @Test
    fun `first success after a failed start emits no events`() {
        val failedStart = SessionsSnapshot(emptyList(), SnapshotStatus.Stale(null, RuntimeProblem.TIMEOUT))

        val reduction = reduce(failedStart, ok(blocked("a"), runtime("b", RuntimePhase.DONE)))

        reduction.events.shouldBeEmpty()
        reduction.snapshot.sessions.map { it.lastActivityAt } shouldBe listOf(T0, T0)
    }

    @Test
    fun `successful listing is fresh at now`() {
        reduce(fresh(emptyList()), ok(runtime("a"))).snapshot.status shouldBe SnapshotStatus.Fresh(NOW)
    }

    @Test
    fun `sessions are ordered by start time then id`() {
        val listing =
            ok(
                runtime("c", startedAt = T0.plusSeconds(60)),
                runtime("b", startedAt = T0),
                runtime("a", startedAt = T0),
            )

        reduce(fresh(emptyList()), listing).snapshot.sessions.map { it.id.value } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `sessions are attributed to the deepest project`() {
        val projects = mapOf(OUTER to Path.of("/p"), INNER to Path.of("/p/a"))
        val listing =
            ok(
                runtime("1", cwd = Path.of("/p/a/sub")),
                runtime("2", cwd = Path.of("/p/ab")),
                runtime("3", cwd = Path.of("/q")),
            )

        val sessions = reduce(fresh(emptyList()), listing, projects).snapshot.sessions

        sessions.map { it.project } shouldBe listOf(INNER, OUTER, null)
    }

    @Test
    fun `changed projects re-attribute sessions without events`() {
        val previous = fresh(listOf(aSession(project = null, lastActivityAt = EARLIER)))

        val reduction = reduce(previous, ok(runtime()), mapOf(INNER to A_PROJECT_DIR))

        reduction.events.shouldBeEmpty()
        reduction.snapshot.sessions shouldBe listOf(aSession(project = INNER, lastActivityAt = EARLIER))
    }

    @Test
    fun `name and cwd follow the listing`() {
        val previous = fresh(listOf(aSession(name = "old-name", cwd = Path.of("/old"), lastActivityAt = EARLIER)))
        val listed = aRuntimeSession(name = "new-name", cwd = A_PROJECT_DIR)

        val session = reduce(previous, ok(listed)).snapshot.sessions.single()

        session.name shouldBe "new-name"
        session.cwd shouldBe A_PROJECT_DIR
    }

    @Test
    fun `summary is kept from the previous snapshot`() {
        val previous = fresh(listOf(aSession(summary = "Fixing the login bug", lastActivityAt = EARLIER)))

        val session = reduce(previous, ok(runtime())).snapshot.sessions.single()

        session.summary shouldBe "Fixing the login bug"
    }

    @Test
    fun `duplicate id keeps the first entry`() {
        val listing =
            ok(
                aRuntimeSession(name = "first", phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input),
                aRuntimeSession(name = "second", phase = RuntimePhase.DONE),
            )

        val sessions = reduce(fresh(emptyList()), listing).snapshot.sessions

        sessions.map { it.name to it.state } shouldBe listOf("first" to AWAITING_INPUT)
    }

    @Test
    fun `events carry the session as shown in the new snapshot`() {
        val previous = fresh(listOf(aSession(summary = "Fixing", lastActivityAt = EARLIER)))

        val reduction = reduce(previous, ok(blocked()))

        val shown = aSession(state = AWAITING_INPUT, lastActivityAt = NOW, summary = "Fixing")
        reduction.snapshot.sessions shouldBe listOf(shown)
        reduction.events shouldBe listOf(SessionEvent.BecameBlocked(shown))
    }

    @Test
    fun `events follow the snapshot order`() {
        val previous =
            fresh(
                listOf(
                    aSession(id = "a", startedAt = T0, lastActivityAt = EARLIER),
                    aSession(id = "b", startedAt = T0.plusSeconds(60), lastActivityAt = EARLIER),
                    aSession(id = "c", startedAt = T0.plusSeconds(120), lastActivityAt = EARLIER),
                ),
            )
        val listing =
            ok(
                runtime("c", RuntimePhase.FAILED, startedAt = T0.plusSeconds(120)),
                blocked("a", startedAt = T0),
                runtime("b", RuntimePhase.DONE, startedAt = T0.plusSeconds(60)),
            )

        val reduction = reduce(previous, listing)

        reduction.events.map { it.session } shouldBe reduction.snapshot.sessions
        reduction.events.map { it::class } shouldBe
            listOf(SessionEvent.BecameBlocked::class, SessionEvent.TurnFinished::class, SessionEvent.Failed::class)
    }

    @Test
    fun `failed listing keeps sessions and marks them stale`() {
        val kept = listOf(aSession(id = "a", lastActivityAt = EARLIER), aSession(id = "b", state = AWAITING_INPUT))

        val snapshot = reduce(fresh(kept), Outcome.Err(RuntimeError.Timeout)).snapshot

        snapshot shouldBe SessionsSnapshot(kept, SnapshotStatus.Stale(LAST_FRESH, RuntimeProblem.TIMEOUT))
    }

    @Test
    fun `failed listing before any success is stale without lastFreshAt`() {
        val snapshot = reduce(SessionsSnapshot.Initial, Outcome.Err(RuntimeError.NotInstalled)).snapshot

        snapshot shouldBe SessionsSnapshot(emptyList(), SnapshotStatus.Stale(null, RuntimeProblem.NOT_INSTALLED))
    }

    @Test
    fun `repeated failure keeps lastFreshAt and reports the latest problem`() {
        val previous = stale(listOf(aSession(lastActivityAt = EARLIER)))

        val snapshot = reduce(previous, Outcome.Err(RuntimeError.Unparseable("json"))).snapshot

        snapshot.status shouldBe SnapshotStatus.Stale(LAST_FRESH, RuntimeProblem.UNPARSEABLE)
    }

    @Test
    fun `failed listing re-attributes kept sessions`() {
        val previous = fresh(listOf(aSession(project = null, state = AWAITING_INPUT, lastActivityAt = EARLIER)))

        val snapshot = reduce(previous, Outcome.Err(RuntimeError.Timeout), mapOf(INNER to A_PROJECT_DIR)).snapshot

        snapshot.sessions shouldBe listOf(aSession(project = INNER, state = AWAITING_INPUT, lastActivityAt = EARLIER))
    }

    @Test
    fun `failed listing emits no events`() {
        val previous =
            fresh(
                listOf(
                    aSession(id = "a", state = AWAITING_INPUT),
                    aSession(id = "b", state = SessionState.Done),
                    aSession(id = "c", state = SessionState.Failed),
                ),
            )

        reduce(previous, Outcome.Err(RuntimeError.CommandFailed(1, "tail"))).events.shouldBeEmpty()
    }

    @Test
    fun `recovery emits transitions made during the outage`() {
        val previous = stale(listOf(aSession(id = "a", lastActivityAt = EARLIER)))
        val listing = ok(blocked("a", startedAt = T0), runtime("b", RuntimePhase.DONE, startedAt = T0.plusSeconds(60)))

        val reduction = reduce(previous, listing)

        reduction.events shouldBe
            listOf(
                SessionEvent.BecameBlocked(aSession(id = "a", state = AWAITING_INPUT, lastActivityAt = NOW)),
                SessionEvent.TurnFinished(
                    aSession(id = "b", state = SessionState.Done, startedAt = T0.plusSeconds(60), lastActivityAt = NOW),
                ),
            )
    }

    @Test
    fun `stale snapshot after recovery is fresh again`() {
        val previous = stale(listOf(aSession(lastActivityAt = EARLIER)))

        reduce(previous, ok(runtime())).snapshot.status shouldBe SnapshotStatus.Fresh(NOW)
    }

    companion object {
        /** Last state change known before the listing under test. */
        private val EARLIER: Instant = T0.plusSeconds(300)

        /** Time of the last successful listing in `previous`. */
        private val LAST_FRESH: Instant = T0.plusSeconds(600)

        /** Time of the listing under test. */
        private val NOW: Instant = T0.plusSeconds(900)

        private val OUTER = ProjectId("outer")
        private val INNER = ProjectId("agent-manager")

        /** The default `aSession` belongs to [INNER] through [A_PROJECT_DIR]. */
        private val PROJECTS: Map<ProjectId, Path> = mapOf(INNER to A_PROJECT_DIR)

        private val AWAITING_INPUT = SessionState.AwaitingInput(question = null)
        private val AWAITING_PERMISSION = SessionState.AwaitingPermission(tool = null)

        private fun reduce(
            previous: SessionsSnapshot,
            listing: Outcome<List<RuntimeSession>, RuntimeError>,
            projects: Map<ProjectId, Path> = PROJECTS,
        ): Reduction = SessionStateReducer.reduce(previous, listing, projects, NOW)

        private fun fresh(sessions: List<Session>): SessionsSnapshot =
            SessionsSnapshot(sessions, SnapshotStatus.Fresh(LAST_FRESH))

        private fun stale(sessions: List<Session>): SessionsSnapshot =
            SessionsSnapshot(sessions, SnapshotStatus.Stale(LAST_FRESH, RuntimeProblem.TIMEOUT))

        private fun ok(vararg sessions: RuntimeSession): Outcome<List<RuntimeSession>, RuntimeError> =
            Outcome.Ok(sessions.toList())

        private fun runtime(
            id: String = aSessionId().value,
            phase: RuntimePhase = RuntimePhase.WORKING,
            waitingFor: WaitingFor? = null,
            startedAt: Instant = T0,
            cwd: Path = A_PROJECT_DIR,
        ): RuntimeSession =
            aRuntimeSession(
                id = aSessionId(id),
                phase = phase,
                waitingFor = waitingFor,
                startedAt = startedAt,
                cwd = cwd,
            )

        private fun blocked(
            id: String = aSessionId().value,
            waitingFor: WaitingFor = WaitingFor.Input,
            startedAt: Instant = T0,
        ): RuntimeSession = runtime(id, RuntimePhase.BLOCKED, waitingFor, startedAt)

        private val becameBlocked: (Session) -> SessionEvent = SessionEvent::BecameBlocked
        private val turnFinished: (Session) -> SessionEvent = SessionEvent::TurnFinished
        private val failed: (Session) -> SessionEvent = SessionEvent::Failed

        @JvmStatic
        fun transitions(): List<Arguments> =
            blockingTransitions() + detailTransitions() + endingTransitions() + appearingAndVanishing()

        private fun blockingTransitions(): List<Arguments> =
            listOf(
                arguments("Working -> BLOCKED/Input", SessionState.Working, blocked(), becameBlocked, NOW),
                arguments(
                    "Working -> BLOCKED/Permission",
                    SessionState.Working,
                    blocked(waitingFor = WaitingFor.Permission),
                    becameBlocked,
                    NOW,
                ),
                arguments(
                    "AwaitingInput -> BLOCKED/Permission",
                    AWAITING_INPUT,
                    blocked(waitingFor = WaitingFor.Permission),
                    becameBlocked,
                    NOW,
                ),
                arguments("AwaitingPermission -> BLOCKED/Input", AWAITING_PERMISSION, blocked(), becameBlocked, NOW),
            )

        private fun detailTransitions(): List<Arguments> =
            listOf(
                arguments("AwaitingInput(null) -> same", AWAITING_INPUT, blocked(), null, EARLIER),
                arguments(
                    "AwaitingPermission(null) -> same",
                    AWAITING_PERMISSION,
                    blocked(waitingFor = WaitingFor.Permission),
                    null,
                    EARLIER,
                ),
                arguments(
                    "AwaitingInput(\"q\") from a hook -> BLOCKED/Input",
                    SessionState.AwaitingInput(question = "q"),
                    blocked(),
                    null,
                    NOW,
                ),
            )

        private fun endingTransitions(): List<Arguments> =
            listOf(
                arguments(
                    "Working -> DONE",
                    SessionState.Working,
                    runtime(phase = RuntimePhase.DONE),
                    turnFinished,
                    NOW,
                ),
                arguments(
                    "Stopped -> DONE",
                    SessionState.Stopped,
                    runtime(phase = RuntimePhase.DONE),
                    turnFinished,
                    NOW,
                ),
                arguments("Working -> FAILED", SessionState.Working, runtime(phase = RuntimePhase.FAILED), failed, NOW),
                arguments("Done -> WORKING", SessionState.Done, runtime(), null, NOW),
                arguments("Failed -> WORKING", SessionState.Failed, runtime(), null, NOW),
                arguments("AwaitingInput -> WORKING", AWAITING_INPUT, runtime(), null, NOW),
                arguments("Working -> STOPPED", SessionState.Working, runtime(phase = RuntimePhase.STOPPED), null, NOW),
                arguments("AwaitingInput -> STOPPED", AWAITING_INPUT, runtime(phase = RuntimePhase.STOPPED), null, NOW),
                arguments("Done -> DONE", SessionState.Done, runtime(phase = RuntimePhase.DONE), null, EARLIER),
                arguments("Working -> WORKING", SessionState.Working, runtime(), null, EARLIER),
            )

        private fun appearingAndVanishing(): List<Arguments> =
            listOf(
                arguments("none -> BLOCKED/Input", null, blocked(), becameBlocked, NOW),
                arguments("none -> DONE", null, runtime(phase = RuntimePhase.DONE), turnFinished, NOW),
                arguments("none -> FAILED", null, runtime(phase = RuntimePhase.FAILED), failed, NOW),
                arguments("none -> WORKING", null, runtime(), null, NOW),
                arguments("none -> STOPPED", null, runtime(phase = RuntimePhase.STOPPED), null, NOW),
                arguments("Working -> none (the session vanishes)", SessionState.Working, null, null, null),
            )
    }
}
