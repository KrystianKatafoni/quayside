package dev.quayside.projects.ui

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
import kotlinx.coroutines.flow.emptyFlow

/** A monitor that never lists: its snapshot stays [SessionsSnapshot.Initial] (Loading), without any clock. */
internal class LoadingSessionMonitor : SessionMonitor {
    override val snapshot: StateFlow<SessionsSnapshot> = MutableStateFlow(SessionsSnapshot.Initial)

    override val events: Flow<SessionEvent> = emptyFlow()

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> = Outcome.Ok(RecentOutput(""))
}
