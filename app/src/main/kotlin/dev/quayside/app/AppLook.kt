package dev.quayside.app

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.appearance.ui.toAmDensity
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.TypographySpec

/**
 * Colours, shape and typography the windows are drawn with, the density of the session list and the size of the
 * whole interface; they come from the user's choice (AM-803).
 */
internal data class AppLook(
    val colors: ColorTokens,
    val shape: ShapeTokens,
    val typography: TypographySpec,
    val density: AmDensity = AmDensity.COMFORTABLE,
    val scale: UiScale = UiScale.M,
)

/**
 * The look of [settings]: colours and shape of its scheme, its typography, the list density mapped to the design
 * system and its UI scale. The ids are in the catalog (AppearanceApi guarantees it), so a miss is a programmer error.
 *
 * @throws IllegalStateException when an id of [settings] is not in this catalog.
 */
internal fun ThemeCatalog.lookOf(settings: AppearanceSettings): AppLook {
    val theme = checkNotNull(theme(settings.scheme)) { "scheme of the settings missing in the catalog" }
    val typography =
        checkNotNull(typography(settings.typography)) { "typography of the settings missing in the catalog" }
    return AppLook(theme.colors, theme.shape, typography, settings.density.toAmDensity(), settings.uiScale)
}
