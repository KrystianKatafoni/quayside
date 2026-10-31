package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/** Stored terminal settings. Safe on any dispatcher; [update] suspends on file IO and never throws. */
public interface TerminalSettingsRepo {
    /** The current settings; the defaults when nothing is stored or the file cannot be used. */
    public val settings: StateFlow<TerminalSettings>

    /** What happened to the file at start; null when nothing. */
    public val issue: StateFlow<TerminalSettingsIssue?>

    /**
     * Validates `change(current)` with [TerminalSettingsRules], saves it and publishes the saved value.
     * [change] must be pure and must not throw.
     */
    public suspend fun update(
        change: (TerminalSettings) -> TerminalSettings,
    ): Outcome<TerminalSettings, TerminalSettingsError>
}
