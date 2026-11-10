package dev.quayside.sessions.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Settings of the disk sessions that are not stored: the default of `sessionsModule` without a settings file. */
internal class InMemoryHistorySettings : SessionHistorySettingsRepo {
    private val state = MutableStateFlow(SessionHistorySettings())

    override val settings: StateFlow<SessionHistorySettings> = state.asStateFlow()

    override suspend fun update(
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ): Outcome<SessionHistorySettings, HistorySettingsError> {
        state.update(change)
        return Outcome.Ok(state.value)
    }
}
