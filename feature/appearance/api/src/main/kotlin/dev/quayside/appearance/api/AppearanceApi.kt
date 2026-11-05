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

    /** The current settings; the defaults until the user applies a change (AM-803: not stored on disk yet). */
    public val settings: StateFlow<AppearanceSettings>

    /**
     * Applies `change(current)` when its ids are in [catalog] and publishes it; otherwise Err, settings unchanged.
     * [change] must be pure. Never throws; safe on any dispatcher.
     */
    public suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError>
}
