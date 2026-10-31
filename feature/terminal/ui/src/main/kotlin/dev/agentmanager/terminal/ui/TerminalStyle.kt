package dev.agentmanager.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One line of text in the panel. */
@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TerminalColors.text,
    size: Int = 13,
    mono: Boolean = false,
) {
    val family = if (mono) FontFamily.Monospace else FontFamily.Default
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontFamily = family),
        maxLines = 1,
    )
}

/**
 * A small click target such as the x of a tab. It never takes the focus: Space and Enter would click it instead of
 * reaching the window shortcuts (Lessons: focusable clickables eat keys).
 */
@Composable
internal fun GlyphButton(
    tag: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .testTag(tag)
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        Label(text = glyph, color = TerminalColors.secondary)
    }
}

/** Placeholder colors until `core:design-system` exists (AM-802). */
internal object TerminalColors {
    val background = Color(color = 0xFF1C1C1E)
    val panel = Color(color = 0xFF2C2C2E)
    val selection = Color(color = 0xFF3A3A3C)
    val warning = Color(color = 0xFF5C4400)
    val accent = Color(color = 0xFF0A84FF)
    val text = Color(color = 0xFFF2F2F7)
    val secondary = Color(color = 0xFF8E8E93)
    val error = Color(color = 0xFFFF453A)

    /** The dot of a tab status; the values match the tones of Mission Control (AM-510). */
    fun of(status: TabStatus): Color =
        when (status) {
            TabStatus.WAITING -> Color(color = 0xFFFFD60A)
            TabStatus.WORKING -> Color(color = 0xFF64D2FF)
            TabStatus.DONE -> Color(color = 0xFF30D158)
            TabStatus.FAILED -> Color(color = 0xFFFF453A)
            TabStatus.STOPPED -> secondary
        }
}
