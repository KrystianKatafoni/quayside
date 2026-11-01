package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionRules
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import java.nio.file.Path
import java.time.Instant

/** Result of one [SessionStateReducer.reduce]: publish [snapshot] first, then [events] (AM-401 contract). */
internal data class Reduction(
    val snapshot: SessionsSnapshot,
    val events: List<SessionEvent>,
)

/**
 * Pure core of the sessions feature (docs/11 §7). Stateless; no IO, no clock, never throws.
 *
 * Rules (AM-402 plan):
 * - Baseline: while [SessionsSnapshot.status] of `previous` has no successful listing
 *   ([SnapshotStatus.Loading] or [SnapshotStatus.Stale] without `lastFreshAt`), the first successful
 *   listing emits no events and sets `lastActivityAt` to `startedAt`.
 * - A successful listing is [SnapshotStatus.Fresh] at `now`; sessions missing from it vanish without
 *   an event, a repeated id keeps its first entry, `summary` is kept from the previous session.
 * - `lastActivityAt` becomes `now` for a session that appears after the baseline or whose state
 *   changed (`!=`); otherwise it is kept.
 * - Events come only when the *kind* of state changes (details such as `question` or `tool` do not
 *   count; a missing session is a kind of its own): waiting → [SessionEvent.BecameBlocked],
 *   [SessionState.Done] → [SessionEvent.TurnFinished], [SessionState.Failed] → [SessionEvent.Failed].
 * - A failed listing keeps the sessions with their states and `lastActivityAt`, emits no events and
 *   yields [SnapshotStatus.Stale] with the last known `lastFreshAt`.
 * - Hook details (AM-603, `details` by session id): the question goes into [SessionState.AwaitingInput],
 *   the tool into [SessionState.AwaitingPermission], and `lastActivityAt` is never before the hook's time.
 *   Details do not count as a change of kind, so they never make an event of their own.
 * - [Session.project] is recomputed from `projects` on every call, without events.
 */
internal object SessionStateReducer {
    /**
     * @param previous the snapshot published so far ([SessionsSnapshot.Initial] on start).
     * @param listing result of `AgentRuntime.listSessions()`; AM-404 passes it through its command overlay first.
     * @param projects registered project dirs for [SessionRules.projectFor].
     * @param now time of the listing (from the injected `Clock` in AM-403).
     * @param details what hooks add to a session, by id; see [HookOverlay].
     * @return sessions ordered by startedAt, then id; events in the same order, only for transitions.
     */
    fun reduce(
        previous: SessionsSnapshot,
        listing: Outcome<List<RuntimeSession>, RuntimeError>,
        projects: Map<ProjectId, Path>,
        now: Instant,
        details: Map<SessionId, HookDetail> = emptyMap(),
    ): Reduction =
        when (listing) {
            is Outcome.Ok -> listed(previous, listing.value, projects, now, details)
            is Outcome.Err -> failed(previous, listing.error, projects)
        }

    private fun listed(
        previous: SessionsSnapshot,
        listing: List<RuntimeSession>,
        projects: Map<ProjectId, Path>,
        now: Instant,
        details: Map<SessionId, HookDetail>,
    ): Reduction {
        val baseline = lastFreshAt(previous.status) == null
        val known = previous.sessions.associateBy { it.id }
        val pairs =
            listing
                .distinctBy { it.id }
                .sortedWith(compareBy<RuntimeSession> { it.startedAt }.thenBy { it.id.value })
                .map { runtime ->
                    known[runtime.id].let { before ->
                        before to
                            runtime.toSession(before, projects, now, baseline, details[runtime.id])
                    }
                }
        val events = if (baseline) emptyList() else pairs.mapNotNull { (before, after) -> eventFor(before, after) }
        return Reduction(SessionsSnapshot(pairs.map { it.second }, SnapshotStatus.Fresh(now)), events)
    }

    private fun failed(
        previous: SessionsSnapshot,
        error: RuntimeError,
        projects: Map<ProjectId, Path>,
    ): Reduction {
        val kept = previous.sessions.map { it.copy(project = SessionRules.projectFor(it.cwd, projects)) }
        val status = SnapshotStatus.Stale(lastFreshAt(previous.status), error.toProblem())
        return Reduction(SessionsSnapshot(kept, status), emptyList())
    }

    private fun RuntimeSession.toSession(
        before: Session?,
        projects: Map<ProjectId, Path>,
        now: Instant,
        baseline: Boolean,
        detail: HookDetail?,
    ): Session {
        val state = toSessionState().withDetail(detail)
        val observed =
            when {
                baseline -> startedAt
                before == null || before.state != state -> now
                else -> before.lastActivityAt
            }
        val lastActivityAt = if (detail != null && detail.at > observed) detail.at else observed
        return Session(
            id = id,
            project = SessionRules.projectFor(cwd, projects),
            name = name,
            cwd = cwd,
            state = state,
            startedAt = startedAt,
            lastActivityAt = lastActivityAt,
            summary = before?.summary,
        )
    }

    private fun SessionState.withDetail(detail: HookDetail?): SessionState =
        when {
            detail == null -> this
            this is SessionState.AwaitingInput -> SessionState.AwaitingInput(detail.question)
            this is SessionState.AwaitingPermission -> SessionState.AwaitingPermission(detail.tool ?: tool)
            else -> this
        }

    private fun eventFor(
        before: Session?,
        after: Session,
    ): SessionEvent? {
        if (before != null && before.state::class == after.state::class) return null
        return when (after.state) {
            is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> SessionEvent.BecameBlocked(after)
            SessionState.Done -> SessionEvent.TurnFinished(after)
            SessionState.Failed -> SessionEvent.Failed(after)
            SessionState.Working, SessionState.Stopped -> null
        }
    }

    private fun lastFreshAt(status: SnapshotStatus): Instant? =
        when (status) {
            SnapshotStatus.Loading -> null
            is SnapshotStatus.Fresh -> status.at
            is SnapshotStatus.Stale -> status.lastFreshAt
        }
}
