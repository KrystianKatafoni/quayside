package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@Composable
internal fun Label(
    text: String,
    color: Color = Colors.text,
    size: Int = 13,
    mono: Boolean = false,
) {
    val family = if (mono) FontFamily.Monospace else FontFamily.Default
    BasicText(text = text, style = TextStyle(color = color, fontSize = size.sp, fontFamily = family), maxLines = 1)
}

/** Placeholder colors until `core:design-system` exists (AM-802). */
internal object Colors {
    val background = Color(color = 0xFF1C1C1E)
    val panel = Color(color = 0xFF2C2C2E)
    val selection = Color(color = 0xFF3A3A3C)
    val button = Color(color = 0xFF48484A)
    val warning = Color(color = 0xFF5C4400)
    val accent = Color(color = 0xFF0A84FF)
    val text = Color(color = 0xFFF2F2F7)
    val secondary = Color(color = 0xFF8E8E93)

    fun of(tone: Tone): Color =
        when (tone) {
            Tone.WARN -> Color(color = 0xFFFFD60A)
            Tone.WORK -> Color(color = 0xFF64D2FF)
            Tone.OK -> Color(color = 0xFF30D158)
            Tone.ERR -> Color(color = 0xFFFF453A)
            Tone.DIM -> secondary
        }
}
