package dev.quayside.sessions.api

import java.time.Instant

/** Pure rules of [FakeSessions] that do not touch its state. */
internal object FakeSessionRules {
    fun statusAfter(
        status: SnapshotStatus,
        available: Boolean,
        now: Instant,
    ): SnapshotStatus =
        when {
            available && status is SnapshotStatus.Fresh -> {
                status
            }

            available -> {
                SnapshotStatus.Fresh(now)
            }

            status is SnapshotStatus.Stale -> {
                status
            }

            else -> {
                SnapshotStatus.Stale(
                    lastFreshAt = (status as? SnapshotStatus.Fresh)?.at,
                    RuntimeProblem.NOT_INSTALLED,
                )
            }
        }

    fun eventFor(session: Session): SessionEvent? =
        when (session.state) {
            is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> SessionEvent.BecameBlocked(session)
            SessionState.Done -> SessionEvent.TurnFinished(session)
            SessionState.Failed -> SessionEvent.Failed(session)
            SessionState.Working, SessionState.Stopped -> null
        }
}
