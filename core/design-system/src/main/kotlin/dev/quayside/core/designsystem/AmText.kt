package dev.quayside.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/** Colour that [AmText] uses when it gets none; set by containers such as [AmModeBar]. Null = content.text. */
internal val LocalTextColor = compositionLocalOf<Color?> { null }

/**
 * One block of text in [role]; [color] null = the colour of the container (content.text by default).
 * [maxLines] 1 = one line with ellipsis (default); more lines wrap and end with an ellipsis.
 *
 * @throws IllegalArgumentException when [maxLines] is less than 1.
 */
@Composable
public fun AmText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.BODY,
    color: Color? = null,
    maxLines: Int = 1,
) {
    val tint =
        color ?: LocalTextColor.current ?: AmTheme.colors.content.text
            .toColor()
    BasicText(
        text = text,
        modifier = modifier,
        style = AmTheme.typography.style(role).copy(color = tint),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
