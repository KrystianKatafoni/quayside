package dev.quayside.notifications.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.NotificationSettingsRepo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [NotificationSettingsRepo] persisted in `notifications.json` through [JsonStore] (ADR 0009).
 *
 * Loaded once by [open]. A broken file is moved aside by the store and the defaults apply; a file from a
 * newer version is never touched, so [update] refuses with [NotificationSettingsError.NEWER_SCHEMA].
 * Each update runs under [mutex] and publishes only after the save succeeded. Logs carry fixed
 * categories only (ENG-C8).
 */
internal class JsonNotificationSettingsRepo private constructor(
    private val store: JsonStore<NotificationSettings>,
    private val logger: Logger,
    initial: NotificationSettings,
    private val newerSchema: Boolean,
) : NotificationSettingsRepo {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val settings: StateFlow<NotificationSettings> = state.asStateFlow()

    override suspend fun update(
        change: (NotificationSettings) -> NotificationSettings,
    ): Outcome<Unit, NotificationSettingsError> {
        if (newerSchema) return Outcome.Err(NotificationSettingsError.NEWER_SCHEMA)
        return mutex.withLock {
            val next = change(state.value)
            // Not cancellable: a cancellation after the atomic move must not leave file and state apart.
            withContext(NonCancellable) {
                when (store.save(next)) {
                    is Outcome.Ok -> {
                        state.value = next
                        Outcome.Ok(Unit)
                    }

                    is Outcome.Err -> {
                        logger.warn("notification settings save failed")
                        Outcome.Err(NotificationSettingsError.WRITE_FAILED)
                    }
                }
            }
        }
    }

    internal companion object {
        const val FILE_NAME = "notifications.json"

        suspend fun open(
            store: JsonStore<NotificationSettings>,
            logger: Logger,
        ): JsonNotificationSettingsRepo =
            when (val loaded = store.load()) {
                is Outcome.Ok -> {
                    if (loaded.value.recovered !=
                        null
                    ) {
                        logger.warn("notification settings recovered from a broken file")
                    }
                    JsonNotificationSettingsRepo(store, logger, loaded.value.value, newerSchema = false)
                }

                is Outcome.Err -> {
                    val newer = loaded.error is StorageError.NewerSchema
                    logger.warn(
                        if (newer) "notification settings file is newer" else "notification settings unreadable",
                    )
                    JsonNotificationSettingsRepo(store, logger, NotificationSettings(), newer)
                }
            }
    }
}
