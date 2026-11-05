package dev.quayside.appearance.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRules

/** Which of the two themes of day/night the theme list edits (FR-A5). */
public enum class ThemeSlot { DAY, NIGHT }

/** The not yet applied choice; [terminalFont] null = from typography; [slot] counts only with day/night on. */
public data class AppearanceDraft(
    val settings: AppearanceSettings,
    val terminalFont: String?,
    val terminalSize: Int,
    val slot: ThemeSlot = ThemeSlot.DAY,
)

/**
 * What failed; no texts from files (ENG-C8). The `IMPORT_*` ones come from reading a theme file, [EXPORT] from
 * writing one.
 */
public enum class ApplyError {
    APPEARANCE,
    TERMINAL,
    IMPORT_UNREADABLE,
    IMPORT_NOT_THEME,
    IMPORT_NEWER,
    IMPORT_INVALID,
    EXPORT,
}

/** The theme the preview shows: `scheme` with day/night off, otherwise the theme of the edited [ThemeSlot]. */
internal fun previewScheme(draft: AppearanceDraft): SchemeId =
    with(draft.settings) {
        when {
            !dayNight.enabled -> scheme
            draft.slot == ThemeSlot.DAY -> dayNight.day
            else -> dayNight.night
        }
    }

/**
 * [draft] with the theme [id] set where the list edits: `scheme` with day/night off, otherwise the day or night theme
 * of the draft's slot.
 */
internal fun selectInSlot(
    draft: AppearanceDraft,
    id: SchemeId,
): AppearanceDraft {
    val settings = draft.settings
    val chosen =
        when {
            !settings.dayNight.enabled -> settings.copy(scheme = id)
            draft.slot == ThemeSlot.DAY -> settings.copy(dayNight = settings.dayNight.copy(day = id))
            else -> settings.copy(dayNight = settings.dayNight.copy(night = id))
        }
    return draft.copy(settings = chosen)
}

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
        .theme(previewScheme(draft))
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
