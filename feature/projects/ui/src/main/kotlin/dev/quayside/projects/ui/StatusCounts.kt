package dev.quayside.projects.ui

import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState

/** Sessions per state on one sidebar row (FR-P6, FR-P7); [SessionState.Stopped] is not counted. */
internal data class StatusCounts(
    val waiting: Int = 0,
    val working: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
) {
    /** Waiting and working only: the tone of the folder icon ([projectTone]). */
    val active: SessionCounts get() = SessionCounts(working = working, waiting = waiting)

    companion object {
        /** Counts [states] into the four fields; a stopped session is nowhere. */
        fun of(states: List<SessionState>): StatusCounts =
            StatusCounts(
                waiting = states.count { it.isWaiting },
                working = states.count { it == SessionState.Working },
                done = states.count { it == SessionState.Done },
                failed = states.count { it == SessionState.Failed },
            )
    }
}
