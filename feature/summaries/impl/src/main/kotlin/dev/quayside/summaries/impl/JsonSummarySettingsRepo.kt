package dev.quayside.summaries.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.summaries.api.SummarySettingsError
import dev.quayside.summaries.api.SummarySettingsRepo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SummarySettingsRepo] persisted in `summaries.json` through [JsonStore] (ADR 0009, ADR 0019).
 *
 * Loaded once by [open]. A broken file is moved aside by the store and the switch is off; a file from a newer
 * version is never touched, so [setUseModel] refuses with [SummarySettingsError.NEWER_SCHEMA]. Saves run under
 * [mutex] and publish only after the save succeeded. Logs carry fixed categories only (ENG-C8).
 */
internal class JsonSummarySettingsRepo private constructor(
    private val store: JsonStore<SummarySettings>,
    private val logger: Logger,
    initial: Boolean,
    private val newerSchema: Boolean,
) : SummarySettingsRepo {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val useModel: StateFlow<Boolean> = state.asStateFlow()

    override val startedWithModel: Boolean = initial

    override suspend fun setUseModel(on: Boolean): Outcome<Unit, SummarySettingsError> {
        if (newerSchema) return Outcome.Err(SummarySettingsError.NEWER_SCHEMA)
        return mutex.withLock {
            // Not cancellable: a cancellation after the atomic move must not leave file and state apart.
            withContext(NonCancellable) {
                when (store.save(SummarySettings(useModel = on))) {
                    is Outcome.Ok -> {
                        state.value = on
                        Outcome.Ok(Unit)
                    }

                    is Outcome.Err -> {
                        logger.warn("summary settings save failed")
                        Outcome.Err(SummarySettingsError.WRITE_FAILED)
                    }
                }
            }
        }
    }

    internal companion object {
        suspend fun open(
            store: JsonStore<SummarySettings>,
            logger: Logger,
        ): JsonSummarySettingsRepo =
            when (val loaded = store.load()) {
                is Outcome.Ok -> {
                    if (loaded.value.recovered != null) logger.warn("summary settings recovered from a broken file")
                    JsonSummarySettingsRepo(store, logger, loaded.value.value.useModel, newerSchema = false)
                }

                is Outcome.Err -> {
                    val newer = loaded.error is StorageError.NewerSchema
                    logger.warn(if (newer) "summary settings file is newer" else "summary settings unreadable")
                    JsonSummarySettingsRepo(store, logger, initial = false, newerSchema = newer)
                }
            }
    }
}
