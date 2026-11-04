package dev.quayside.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText

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
        AmText(
            text = glyph,
            color = mutedColor(),
        )
    }
}
