package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.SessionState

/** Counters of the four stat cards of the project page; stopped sessions are not counted. */
internal data class SessionStats(
    val waiting: Int = 0,
    val working: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
) {
    companion object {
        /** Counts [states]: waiting = awaiting input or permission, then working, done and failed. */
        fun of(states: List<SessionState>): SessionStats =
            SessionStats(
                waiting = states.count { it.isWaiting },
                working = states.count { it == SessionState.Working },
                done = states.count { it == SessionState.Done },
                failed = states.count { it == SessionState.Failed },
            )
    }
}
