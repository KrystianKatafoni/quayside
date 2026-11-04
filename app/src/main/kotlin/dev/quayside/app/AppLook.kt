package dev.quayside.app

import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.TypographySpec

/** Colours, shape and typography the windows are drawn with; AM-804 replaces the source with the user's choice. */
internal data class AppLook(
    val colors: ColorTokens,
    val shape: ShapeTokens,
    val typography: TypographySpec,
)

/**
 * Default scheme and typography (plex); ThemeCatalog guarantees both, so a miss is a programmer error.
 *
 * @throws IllegalStateException never for the built-in catalog.
 */
internal fun ThemeCatalog.defaultLook(): AppLook {
    val theme = checkNotNull(theme(ThemeCatalog.DEFAULT_SCHEME)) { "default scheme missing" }
    val typography = checkNotNull(typography(ThemeCatalog.DEFAULT_TYPOGRAPHY)) { "default typography missing" }
    return AppLook(theme.colors, theme.shape, typography)
}
