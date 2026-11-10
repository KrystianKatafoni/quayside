package dev.quayside.sessions.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SessionHistorySettingsRepo] persisted in `session-history.json` through [JsonStore] (ADR 0032, ADR 0024).
 *
 * Loaded once by [open]. A broken file is moved aside by the store and the defaults apply; a file from a newer
 * version is never touched, so [update] refuses with [HistorySettingsError.NEWER_SCHEMA]. Saves run under [mutex]
 * and publish only after the save succeeded. Logs carry fixed categories only (ENG-C8).
 */
internal class JsonSessionHistorySettingsRepo private constructor(
    private val store: JsonStore<SessionHistorySettings>,
    private val logger: Logger,
    initial: SessionHistorySettings,
    private val newerSchema: Boolean,
) : SessionHistorySettingsRepo {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val settings: StateFlow<SessionHistorySettings> = state.asStateFlow()

    override suspend fun update(
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ): Outcome<SessionHistorySettings, HistorySettingsError> {
        if (newerSchema) return Outcome.Err(HistorySettingsError.NEWER_SCHEMA)
        return mutex.withLock {
            // Not cancellable: a cancellation after the atomic move must not leave file and state apart.
            withContext(NonCancellable) {
                val next = change(state.value)
                when (store.save(next)) {
                    is Outcome.Ok -> {
                        state.value = next
                        Outcome.Ok(next)
                    }

                    is Outcome.Err -> {
                        logger.warn("session history settings save failed")
                        Outcome.Err(HistorySettingsError.WRITE_FAILED)
                    }
                }
            }
        }
    }

    internal companion object {
        suspend fun open(
            store: JsonStore<SessionHistorySettings>,
            logger: Logger,
        ): JsonSessionHistorySettingsRepo =
            when (val loaded = store.load()) {
                is Outcome.Ok -> {
                    if (loaded.value.recovered != null) logger.warn("session history settings recovered")
                    JsonSessionHistorySettingsRepo(store, logger, loaded.value.value, newerSchema = false)
                }

                is Outcome.Err -> {
                    val newer = loaded.error is StorageError.NewerSchema
                    logger.warn(if (newer) "session history file is newer" else "session history unreadable")
                    JsonSessionHistorySettingsRepo(store, logger, SessionHistorySettings(), newerSchema = newer)
                }
            }
    }
}
