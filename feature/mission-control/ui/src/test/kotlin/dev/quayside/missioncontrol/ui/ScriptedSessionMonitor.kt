package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [SessionMonitor] whose snapshot the test sets directly: summaries and stale snapshots that `FakeSessions`
 * cannot produce. Mission Control reads [snapshot] and [recentOutput] (always empty here); [events] stays empty.
 */
internal class ScriptedSessionMonitor(
    initial: SessionsSnapshot,
) : SessionMonitor {
    private val current = MutableStateFlow(initial)

    override val snapshot: StateFlow<SessionsSnapshot> = current.asStateFlow()

    override val events: Flow<SessionEvent> = emptyFlow()

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> = Outcome.Ok(RecentOutput(""))

    fun show(snapshot: SessionsSnapshot) {
        current.value = snapshot
    }
}
