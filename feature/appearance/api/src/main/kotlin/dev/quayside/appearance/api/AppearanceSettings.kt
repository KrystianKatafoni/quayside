package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId

/** Size of the whole interface; [factor] multiplies the window density (docs/08: S/M/L). */
@Suppress("MagicNumber") // the three factors of docs/08
public enum class UiScale(
    public val factor: Float,
) {
    S(0.9f),
    M(1.0f),
    L(1.15f),
}

/** Density of the session list (docs/08); `app` and `appearance:ui` map it to the design system. */
public enum class ListDensity { COMPACT, COMFORTABLE }

/** Day and night themes picked by the macOS mode (FR-A5); [enabled] false = always `AppearanceSettings.scheme`. */
public data class DayNight(
    val enabled: Boolean = false,
    val day: SchemeId = DEFAULT_DAY,
    val night: SchemeId = ThemeCatalog.DEFAULT_SCHEME,
) {
    public companion object {
        /** `paper`. */
        public val DEFAULT_DAY: SchemeId = SchemeId("paper")
    }
}

/** What the user chose; ids always exist in [AppearanceApi.catalog]. Immutable. */
public data class AppearanceSettings(
    val scheme: SchemeId = ThemeCatalog.DEFAULT_SCHEME,
    val typography: TypographyId = ThemeCatalog.DEFAULT_TYPOGRAPHY,
    val uiScale: UiScale = UiScale.M,
    val density: ListDensity = ListDensity.COMFORTABLE,
    val dayNight: DayNight = DayNight(),
)

/**
 * The settings in effect: [this] with `scheme` set to the day or night theme when day/night is on and [systemDark]
 * is known; otherwise [this] unchanged. Pure.
 */
public fun AppearanceSettings.appliedFor(systemDark: Boolean?): AppearanceSettings =
    when {
        !dayNight.enabled || systemDark == null -> this
        systemDark -> copy(scheme = dayNight.night)
        else -> copy(scheme = dayNight.day)
    }
