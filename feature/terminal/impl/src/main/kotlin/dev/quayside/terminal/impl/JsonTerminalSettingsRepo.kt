package dev.quayside.terminal.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.Loaded
import dev.quayside.core.storage.StorageError
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsError
import dev.quayside.terminal.api.TerminalSettingsIssue
import dev.quayside.terminal.api.TerminalSettingsRepo
import dev.quayside.terminal.api.TerminalSettingsRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [TerminalSettingsRepo] persisted in `terminal.json` through [JsonStore] (ADR 0016). When the file could not be
 * read ([writable] is false) the defaults are used and nothing is ever written over the file.
 */
internal class JsonTerminalSettingsRepo private constructor(
    private val store: JsonStore<TerminalSettings>,
    private val logger: Logger,
    initial: TerminalSettings,
    startIssue: TerminalSettingsIssue?,
    private val writable: Boolean,
) : TerminalSettingsRepo {
    private val mutex = Mutex()
    private val current = MutableStateFlow(initial)
    private val problem = MutableStateFlow(startIssue)

    override val settings: StateFlow<TerminalSettings> get() = current

    override val issue: StateFlow<TerminalSettingsIssue?> get() = problem

    override suspend fun update(
        change: (TerminalSettings) -> TerminalSettings,
    ): Outcome<TerminalSettings, TerminalSettingsError> =
        mutex.withLock {
            when (val valid = TerminalSettingsRules.validate(change(current.value))) {
                is Outcome.Err -> valid
                is Outcome.Ok -> save(valid.value)
            }
        }

    private suspend fun save(value: TerminalSettings): Outcome<TerminalSettings, TerminalSettingsError> =
        if (writable && store.save(value) is Outcome.Ok) {
            current.value = value
            logger.info("terminal settings saved")
            Outcome.Ok(value)
        } else {
            logger.warn("terminal settings not saved", "reason" to "storageFailed")
            Outcome.Err(TerminalSettingsError.StorageFailed)
        }

    internal companion object {
        const val FILE_NAME = "terminal.json"

        /** Loads [store] once; the repo then holds the value in memory. [logger] gets fixed categories only. */
        suspend fun open(
            store: JsonStore<TerminalSettings>,
            logger: Logger,
        ): JsonTerminalSettingsRepo =
            when (val loaded = store.load()) {
                is Outcome.Ok -> fromLoaded(store, logger, loaded.value)
                is Outcome.Err -> unusable(store, logger, loaded.error)
            }

        private fun fromLoaded(
            store: JsonStore<TerminalSettings>,
            logger: Logger,
            loaded: Loaded<TerminalSettings>,
        ): JsonTerminalSettingsRepo {
            val issue = loaded.recovered?.let { TerminalSettingsIssue.RECOVERED_FROM_BROKEN_FILE }
            if (issue != null) logger.warn("terminal settings file recovered", "reason" to "recovered")
            return JsonTerminalSettingsRepo(store, logger, loaded.value, issue, writable = true)
        }

        private fun unusable(
            store: JsonStore<TerminalSettings>,
            logger: Logger,
            error: StorageError,
        ): JsonTerminalSettingsRepo {
            val newer = error is StorageError.NewerSchema
            val issue = if (newer) TerminalSettingsIssue.NEWER_SCHEMA else TerminalSettingsIssue.READ_FAILED
            logger.warn("terminal settings file unusable", "reason" to if (newer) "newerSchema" else "readFailed")
            return JsonTerminalSettingsRepo(store, logger, TerminalSettings(), issue, writable = false)
        }
    }
}
