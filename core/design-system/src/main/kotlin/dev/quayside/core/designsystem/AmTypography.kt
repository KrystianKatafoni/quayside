package dev.quayside.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.TypographySpec

/** Text roles of the design system; sizes in sp: TITLE 20, HEADING 15, BODY 13, CAPTION 11, CODE 13 (mono). */
public enum class TextRole {
    TITLE,
    HEADING,
    BODY,
    CAPTION,
    CODE,

    /** Mono 15 sp, SemiBold: session names. */
    NAME,

    /** Ui 11 sp, SemiBold, letter spacing 0.12 em: section labels. */
    LABEL,
}

/**
 * UI and mono families of a typography and the text style of every role (TITLE, HEADING, NAME and LABEL SemiBold,
 * rest Normal).
 */
@Immutable
public class AmTypography(
    public val ui: FontFamily,
    public val mono: FontFamily,
) {
    /** Style of [role]: [mono] for CODE and NAME, [ui] for the rest; no colour. */
    public fun style(role: TextRole): TextStyle =
        when (role) {
            TextRole.TITLE -> textStyle(ui, TITLE_SP, FontWeight.SemiBold)
            TextRole.HEADING -> textStyle(ui, HEADING_SP, FontWeight.SemiBold)
            TextRole.BODY -> textStyle(ui, BODY_SP, FontWeight.Normal)
            TextRole.CAPTION -> textStyle(ui, CAPTION_SP, FontWeight.Normal)
            TextRole.CODE -> textStyle(mono, BODY_SP, FontWeight.Normal)
            TextRole.NAME -> textStyle(mono, HEADING_SP, FontWeight.SemiBold)
            TextRole.LABEL -> textStyle(ui, CAPTION_SP, FontWeight.SemiBold).copy(letterSpacing = LABEL_SPACING.em)
        }

    private fun textStyle(
        family: FontFamily,
        size: Int,
        weight: FontWeight,
    ) = TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight)

    private companion object {
        const val TITLE_SP = 20
        const val HEADING_SP = 15
        const val BODY_SP = 13
        const val CAPTION_SP = 11
        const val LABEL_SPACING = 0.12
    }
}

/** Maps a typography spec to [AmTypography]; unknown names fall back to the default (ui) and monospace (terminal). */
public fun TypographySpec.toAmTypography(): AmTypography =
    AmTypography(
        ui = AmFonts.family(uiFont) ?: FontFamily.Default,
        mono = AmFonts.family(terminalFont) ?: FontFamily.Monospace,
    )
