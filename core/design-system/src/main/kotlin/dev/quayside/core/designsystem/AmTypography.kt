package dev.quayside.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.TypographySpec

/** Text roles of the design system; sizes in sp: TITLE 20, HEADING 15, BODY 13, CAPTION 11, CODE 13 (mono). */
public enum class TextRole { TITLE, HEADING, BODY, CAPTION, CODE }

/** UI and mono families of a typography and the text style of every role (TITLE/HEADING SemiBold, rest Normal). */
@Immutable
public class AmTypography(
    public val ui: FontFamily,
    public val mono: FontFamily,
) {
    /** Style of [role]: [mono] for CODE, [ui] for the rest; no colour. */
    public fun style(role: TextRole): TextStyle =
        when (role) {
            TextRole.TITLE -> textStyle(ui, TITLE_SP, FontWeight.SemiBold)
            TextRole.HEADING -> textStyle(ui, HEADING_SP, FontWeight.SemiBold)
            TextRole.BODY -> textStyle(ui, BODY_SP, FontWeight.Normal)
            TextRole.CAPTION -> textStyle(ui, CAPTION_SP, FontWeight.Normal)
            TextRole.CODE -> textStyle(mono, BODY_SP, FontWeight.Normal)
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
    }
}

/** Maps a typography spec to [AmTypography]; unknown names fall back to the default (ui) and monospace (terminal). */
public fun TypographySpec.toAmTypography(): AmTypography =
    AmTypography(
        ui = AmFonts.family(uiFont) ?: FontFamily.Default,
        mono = AmFonts.family(terminalFont) ?: FontFamily.Monospace,
    )
