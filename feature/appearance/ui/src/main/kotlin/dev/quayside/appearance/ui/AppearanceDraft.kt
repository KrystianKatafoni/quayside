package dev.quayside.appearance.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRules

/** The not yet applied choice; [terminalFont] null = from typography. */
public data class AppearanceDraft(
    val settings: AppearanceSettings,
    val terminalFont: String?,
    val terminalSize: Int,
)

/** Which save failed; no texts from files (ENG-C8). */
public enum class ApplyError { APPEARANCE, TERMINAL }

/** The one mapping of the stored density to the design system; used by the preview and by `app`. */
public fun ListDensity.toAmDensity(): AmDensity =
    when (this) {
        ListDensity.COMPACT -> AmDensity.COMPACT
        ListDensity.COMFORTABLE -> AmDensity.COMFORTABLE
    }

/** The draft that starts from the saved [settings] and the saved terminal font and size of [terminal]. */
internal fun draftOf(
    settings: AppearanceSettings,
    terminal: TerminalSettings,
): AppearanceDraft = AppearanceDraft(settings, terminal.fontFamily, terminal.fontSize)

/**
 * The default typography of the theme chosen in [draft] when it differs from the chosen typography; null when the
 * theme has none, it is the chosen one already, or the theme is not in [catalog].
 */
internal fun suggestedTypography(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
): TypographyId? =
    catalog
        .theme(draft.settings.scheme)
        ?.defaultTypography
        ?.takeIf { it != draft.settings.typography }

/** [size] moved by [delta], kept inside `TerminalSettingsRules.FONT_SIZES` (no wrap). */
internal fun stepSize(
    size: Int,
    delta: Int,
): Int = (size + delta).coerceIn(TerminalSettingsRules.FONT_SIZES)

/** The terminal fonts to choose from: null ("from typography") first, then the catalog's fonts once, in order. */
internal fun terminalFontChoices(catalog: ThemeCatalog): List<String?> =
    listOf<String?>(null) + catalog.typographies.map { it.terminalFont }.distinct()

private const val WINDOW_WIDTH_DP = 1280f

/**
 * The density the preview is drawn with: [base] scaled so that a window of 1280 dp fits [paneWidth], times the
 * [scale] factor; the font scale stays.
 */
internal fun previewDensity(
    base: Density,
    paneWidth: Dp,
    scale: UiScale,
): Density = Density(base.density * paneWidth.value / WINDOW_WIDTH_DP * scale.factor, base.fontScale)
