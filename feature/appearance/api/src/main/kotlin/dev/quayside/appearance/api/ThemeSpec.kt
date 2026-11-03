package dev.quayside.appearance.api

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.TypographyId

/** Light or dark, normal or high contrast. */
public enum class ThemeMode {
    LIGHT,
    DARK,
    LIGHT_HIGH_CONTRAST,
    DARK_HIGH_CONTRAST,
    ;

    /** True for [DARK] and [DARK_HIGH_CONTRAST]. */
    public val isDark: Boolean get() = this == DARK || this == DARK_HIGH_CONTRAST

    /** True for [LIGHT_HIGH_CONTRAST] and [DARK_HIGH_CONTRAST]. */
    public val isHighContrast: Boolean get() = this == LIGHT_HIGH_CONTRAST || this == DARK_HIGH_CONTRAST
}

/** One built-in or custom theme; [defaultTypography] is only suggested by the Appearance screen, null = none. */
public data class ThemeSpec(
    val id: SchemeId,
    val name: String,
    val mode: ThemeMode,
    val colors: ColorTokens,
    val shape: ShapeTokens,
    val defaultTypography: TypographyId?,
)
