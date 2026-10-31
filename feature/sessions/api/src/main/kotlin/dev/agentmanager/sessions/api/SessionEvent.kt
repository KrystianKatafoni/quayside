package dev.agentmanager.sessions.api

import kotlin.time.Duration

/**
 * A state transition of a session (FR-N1–N3 consume them). Only transitions are events: sessions
 * already present in the first snapshot emit nothing for the state they were found in. A session
 * that first appears later while waiting emits [BecameBlocked].
 */
public sealed interface SessionEvent {
    /** The session after the transition, as [SessionMonitor.snapshot] shows it. */
    public val session: Session

    /** The session started waiting: [SessionState.AwaitingInput] or [SessionState.AwaitingPermission]. */
    public data class BecameBlocked(
        override val session: Session,
    ) : SessionEvent

    /** The session finished its turn: [SessionState.Done]. */
    public data class TurnFinished(
        override val session: Session,
    ) : SessionEvent

    /** The session ended with an error: [SessionState.Failed]. */
    public data class Failed(
        override val session: Session,
    ) : SessionEvent

    /** The session opened a pull request. Not emitted until the task that detects it. */
    public data class PullRequestReady(
        override val session: Session,
    ) : SessionEvent

    /** The session has been working for [duration]. Not emitted until the task that detects it. */
    public data class RunningLong(
        override val session: Session,
        val duration: Duration,
    ) : SessionEvent
}
