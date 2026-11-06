package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

private val MIN_SIZE = 22.dp
private val SIDE_PADDING = 6.dp

/**
 * Count in a tone circle (the theme counter shape, square when the token radius is 0), at least 22 dp wide and high,
 * wider for long numbers. [description] is read instead of the number ("2 czekają"). Not focusable, not clickable.
 */
@Composable
public fun AmCounter(
    count: Int,
    tone: StatusTone,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = chipColors(tone, AmTheme.colors)
    Box(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = description }
                .defaultMinSize(MIN_SIZE, MIN_SIZE)
                .background(colors.background.toColor(), AmTheme.shape.counter)
                .padding(horizontal = SIDE_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        AmText(count.toString(), role = TextRole.CAPTION, color = colors.label.toColor())
    }
}
