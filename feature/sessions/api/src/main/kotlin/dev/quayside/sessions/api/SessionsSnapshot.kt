package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import java.time.Instant

/**
 * Every session known at one moment.
 *
 * @property sessions ordered by [Session.startedAt], then by [Session.id].
 * @property status whether [sessions] come from a successful listing.
 */
public data class SessionsSnapshot(
    val sessions: List<Session>,
    val status: SnapshotStatus,
) {
    /**
     * Pure: active sessions (see [SessionState.isActive]) whose [Session.project] is [project]
     * (FR-P6). Sessions of nested projects and sessions without a project are not counted.
     */
    public fun countsFor(project: ProjectId): SessionCounts {
        val active = sessions.filter { it.project == project && it.state.isActive }
        return SessionCounts(
            working = active.count { it.state == SessionState.Working },
            waiting = active.count { it.state.isWaiting },
        )
    }

    public companion object {
        /** Before the first listing: no sessions, [SnapshotStatus.Loading]. */
        public val Initial: SessionsSnapshot = SessionsSnapshot(emptyList(), SnapshotStatus.Loading)
    }
}

/**
 * Active sessions of one project.
 *
 * @property working sessions in [SessionState.Working].
 * @property waiting sessions that wait for the user (see [SessionState.isWaiting]).
 */
public data class SessionCounts(
    val working: Int,
    val waiting: Int,
) {
    /** Every active session: [working] plus [waiting]. */
    val active: Int get() = working + waiting
}

/** Freshness of a [SessionsSnapshot] (docs/11 §7): a failed refresh keeps the last sessions and marks them stale. */
public sealed interface SnapshotStatus {
    /** Nothing has been listed yet. */
    public data object Loading : SnapshotStatus

    /** The sessions come from a listing made [at]. */
    public data class Fresh(
        val at: Instant,
    ) : SnapshotStatus

    /**
     * The last refresh failed with [problem]; the sessions are the ones listed at [lastFreshAt], or
     * none when no listing has succeeded yet.
     */
    public data class Stale(
        val lastFreshAt: Instant?,
        val problem: RuntimeProblem,
    ) : SnapshotStatus
}
