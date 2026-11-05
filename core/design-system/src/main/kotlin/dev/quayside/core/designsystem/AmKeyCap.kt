package dev.quayside.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keyboard shortcut mark ([keys], e.g. "Cmd K", "Esc"): CODE text, muted, border2 frame. Read as plain text. */
@Composable
public fun AmKeyCap(
    keys: String,
    modifier: Modifier = Modifier,
) {
    val (text, border) = keyCapColors(AmTheme.colors)
    val shape = AmTheme.shape
    Box(
        modifier =
            modifier
                .border(shape.borderWidth, border.toColor(), shape.corner)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        AmText(keys, role = TextRole.CODE, color = text.toColor())
    }
}
