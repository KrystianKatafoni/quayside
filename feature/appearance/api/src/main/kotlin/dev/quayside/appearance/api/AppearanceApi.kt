package dev.quayside.appearance.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Appearance feature contract; AM-804 adds theme.json and day/night behind the same members. Thread-safe, never
 * blocks.
 */
public interface AppearanceApi {
    /** The catalog: the built-in themes followed by the custom ones (FR-A6); changes only after [addScheme]. */
    public val catalog: StateFlow<ThemeCatalog>

    /** The saved choice (theme.json); what the Appearance dialog edits. */
    public val settings: StateFlow<AppearanceSettings>

    /** What the app draws now: `settings.appliedFor(macOS is dark)`; follows macOS within about 5 s.
     * Right after day/night is switched on, it equals [settings] until the first mode read finishes (a short flash). */
    public val applied: StateFlow<AppearanceSettings>

    /**
     * Applies `change(current)` when all its ids (also day/night) are in [catalog], saves theme.json and publishes it;
     * otherwise Err, settings unchanged ([AppearanceError.StorageFailed] when the file could not be written).
     * [change] must be pure; it never changes `customSchemes` (the result keeps the saved ones, see [addScheme]).
     * Never throws; safe on any dispatcher.
     */
    public suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError>

    /**
     * Adds [scheme] to the custom schemes: derives its tokens, checks NFR-6 (see `ContrastAudit`), saves theme.json
     * and publishes the new [catalog]. Err and nothing changed when the id is taken, the contrast is too low or the
     * file could not be written. Never throws; safe on any dispatcher.
     */
    public suspend fun addScheme(scheme: CustomScheme): Outcome<ThemeSpec, SchemeError>
}
