package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [SummarySettingsRepo] for tests of other modules. Passes [SummarySettingsRepoContract]. [failure] makes
 * the next saves fail with that error and publish nothing.
 */
public class FakeSummarySettingsRepo(
    initial: Boolean = false,
) : SummarySettingsRepo {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    /** The error every [setUseModel] returns while set; null = saves work. */
    public var failure: SummarySettingsError? = null

    override val useModel: StateFlow<Boolean> = state.asStateFlow()

    override val startedWithModel: Boolean = initial

    override suspend fun setUseModel(on: Boolean): Outcome<Unit, SummarySettingsError> =
        mutex.withLock {
            val error = failure
            if (error != null) {
                Outcome.Err(error)
            } else {
                state.value = on
                Outcome.Ok(Unit)
            }
        }
}
