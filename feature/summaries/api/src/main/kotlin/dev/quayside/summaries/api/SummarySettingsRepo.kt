package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * The stored switch of model summaries (FR-S7, ADR 0019). Threading: safe to call from any dispatcher; changes are
 * applied one after another.
 */
public interface SummarySettingsRepo {
    /** The saved switch; false (model off) when nothing is stored or the stored file could not be used. */
    public val useModel: StateFlow<Boolean>

    /**
     * The switch the running app started with. `summariesModule` reads the file once at start, so a change of
     * [useModel] takes effect at the next start; a screen shows that while the two differ.
     */
    public val startedWithModel: Boolean

    /** Saves [on] and then publishes it in [useModel]. Nothing is published when saving fails. */
    public suspend fun setUseModel(on: Boolean): Outcome<Unit, SummarySettingsError>
}

/** Why [SummarySettingsRepo.setUseModel] changed nothing. */
public enum class SummarySettingsError {
    /** The file could not be written (permissions, full disk). */
    WRITE_FAILED,

    /** The file was written by a newer version of the app; it is never overwritten. */
    NEWER_SCHEMA,
}
