package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory [SessionHistorySettingsRepo] (ENG-I7). Passes [SessionHistorySettingsContract].
 *
 * [failNext] makes the next [update] return `Err` and change nothing. [updates] counts the successful saves.
 */
public class FakeSessionHistorySettings(
    initial: SessionHistorySettings = SessionHistorySettings(),
) : SessionHistorySettingsRepo {
    private val state = MutableStateFlow(initial)
    private val failure = AtomicReference<HistorySettingsError?>(null)
    private val saved = MutableStateFlow(0)
    private val listeners = AtomicReference<List<() -> Unit>>(emptyList())

    override val settings: StateFlow<SessionHistorySettings> = state.asStateFlow()

    /** Successful [update] calls so far. */
    public val updates: Int get() = saved.value

    /** The next [update] returns `Err(error)` and saves nothing; later calls work again. */
    public fun failNext(error: HistorySettingsError) {
        failure.set(error)
    }

    /** [FakeSessions] hook: called after every saved change, synchronously, so its snapshot follows at once. */
    internal fun onSaved(listener: () -> Unit) {
        listeners.updateAndGet { it + listener }
    }

    override suspend fun update(
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ): Outcome<SessionHistorySettings, HistorySettingsError> {
        failure.getAndSet(null)?.let { return Outcome.Err(it) }
        val after = change(state.value)
        state.value = after
        saved.value = saved.value + 1
        listeners.get().forEach { it() }
        return Outcome.Ok(after)
    }
}
