package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [TerminalSettingsRepo] for tests of other features: same validation as `impl`
 * ([TerminalSettingsRules]), no file. A test sets [failure] to simulate a store that cannot be written
 * and [issue] to simulate a recovered file. Starts with the defaults (`optionAsMeta` off) unless told otherwise.
 */
public class FakeTerminalSettingsRepo(
    initial: TerminalSettings = TerminalSettings(),
    startIssue: TerminalSettingsIssue? = null,
) : TerminalSettingsRepo {
    private val mutex = Mutex()
    private val current = MutableStateFlow(initial)
    private val problem = MutableStateFlow(startIssue)

    /** When set, [update] fails with [TerminalSettingsError.StorageFailed] and keeps the settings. */
    @Volatile
    public var failure: Boolean = false

    /** Number of successful saves. */
    @Volatile
    public var saves: Int = 0
        private set

    override val settings: StateFlow<TerminalSettings> get() = current

    override val issue: StateFlow<TerminalSettingsIssue?> get() = problem

    override suspend fun update(
        change: (TerminalSettings) -> TerminalSettings,
    ): Outcome<TerminalSettings, TerminalSettingsError> =
        mutex.withLock {
            TerminalSettingsRules.validate(change(current.value)).flatMap { valid ->
                if (failure) return@flatMap Outcome.Err(TerminalSettingsError.StorageFailed)
                current.value = valid
                saves++
                Outcome.Ok(valid)
            }
        }
}
