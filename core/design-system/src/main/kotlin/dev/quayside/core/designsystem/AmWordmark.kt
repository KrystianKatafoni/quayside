package dev.quayside.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over

private const val MIX_STEP = 5
private const val MIX_FULL = 100
private const val FONT_SIZE = 15

/**
 * "Quayside" as one text node: "Quay" in content.text, "side" in [wordmarkAccent], IBM Plex Sans SemiBold 15 sp
 * whatever the theme typography. Not focusable.
 */
@Composable
public fun AmWordmark(modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val text = remember(colors) { wordmarkText(colors) }
    BasicText(text = text, modifier = modifier, style = wordmarkStyle(), maxLines = 1)
}

private fun wordmarkText(colors: ColorTokens): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = colors.content.text.toColor())) { append("Quay") }
        withStyle(SpanStyle(color = colors.wordmarkAccent().toColor())) { append("side") }
    }

/**
 * Colour of "side": the accent when it has 4.5:1 on the panel as drawn (panel over bg), else the accent mixed toward
 * content.text in 5% steps until it does (text itself as the last step, which every theme guarantees). Pure.
 */
public fun ColorTokens.wordmarkAccent(): Rgba {
    val panel = surfaces.panel.over(surfaces.bg)
    return (0..MIX_FULL step MIX_STEP)
        .asSequence()
        .map { percent -> content.accent.mix(content.text, percent) }
        .firstOrNull { Wcag.contrast(it, panel) >= Wcag.TEXT_MIN } ?: content.text
}

/** Style of the wordmark: IBM Plex Sans SemiBold 15 sp, no colour. */
internal fun wordmarkStyle(): TextStyle =
    TextStyle(fontFamily = AmFonts.family("IBM Plex Sans"), fontWeight = FontWeight.SemiBold, fontSize = FONT_SIZE.sp)
