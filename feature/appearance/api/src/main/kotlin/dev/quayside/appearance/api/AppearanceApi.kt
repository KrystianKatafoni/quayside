package dev.quayside.appearance.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Appearance feature contract; AM-804 adds theme.json and day/night behind the same members. Thread-safe, never
 * blocks.
 */
public interface AppearanceApi {
    /** The catalog; the same instance on every read. */
    public val catalog: ThemeCatalog

    /** The saved choice (theme.json); what the Appearance dialog edits. */
    public val settings: StateFlow<AppearanceSettings>

    /** What the app draws now: `settings.appliedFor(macOS is dark)`; follows macOS within about 5 s.
     * Right after day/night is switched on, it equals [settings] until the first mode read finishes (a short flash). */
    public val applied: StateFlow<AppearanceSettings>

    /**
     * Applies `change(current)` when all its ids (also day/night) are in [catalog], saves theme.json and publishes it;
     * otherwise Err, settings unchanged ([AppearanceError.StorageFailed] when the file could not be written).
     * [change] must be pure. Never throws; safe on any dispatcher.
     */
    public suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError>
}
