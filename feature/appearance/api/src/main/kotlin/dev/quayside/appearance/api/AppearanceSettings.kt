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

/** What the user chose; ids always exist in [AppearanceApi.catalog]. Immutable. */
public data class AppearanceSettings(
    val scheme: SchemeId = ThemeCatalog.DEFAULT_SCHEME,
    val typography: TypographyId = ThemeCatalog.DEFAULT_TYPOGRAPHY,
    val uiScale: UiScale = UiScale.M,
    val density: ListDensity = ListDensity.COMFORTABLE,
)
