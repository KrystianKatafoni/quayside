package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory [NotificationSettingsRepo] for tests of other modules. Passes [NotificationSettingsRepoContract]. */
public class FakeNotificationSettingsRepo(
    initial: NotificationSettings = NotificationSettings(),
) : NotificationSettingsRepo {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val settings: StateFlow<NotificationSettings> = state.asStateFlow()

    override suspend fun update(
        change: (NotificationSettings) -> NotificationSettings,
    ): Outcome<Unit, NotificationSettingsError> =
        mutex.withLock {
            state.value = change(state.value)
            Outcome.Ok(Unit)
        }
}
