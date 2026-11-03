package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Bottom status bar: panel background, border on top and the muted text colour for [AmText] inside. Callers pass
 * `TextRole.CAPTION` for its labels.
 */
@Composable
public fun AmModeBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AmTheme.colors
    val (background, text) = modeBarColors(colors)
    val border = modeBarBorder(colors).toColor()
    val width = AmTheme.shape.borderWidth
    CompositionLocalProvider(LocalTextColor provides text.toColor()) {
        Row(
            modifier =
                modifier
                    .background(background.toColor())
                    .drawBehind { drawLine(border, Offset.Zero, Offset(size.width, 0f), width.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Mode label like `NORMAL`: accentInk on accent. */
@Composable
public fun AmModeBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val (background, label) = badgeColors(AmTheme.colors)
    AmText(
        text = text,
        modifier =
            modifier
                .clip(AmTheme.shape.corner)
                .background(background.toColor())
                .padding(horizontal = 8.dp, vertical = 2.dp),
        role = TextRole.CAPTION,
        color = label.toColor(),
    )
}
