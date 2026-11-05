package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Counter tile: [AmIconTile] (42 dp) and [value] (TITLE) above [label] (CAPTION, muted), on panel with the theme
 * border. Read as one node ("value label"); not focusable, not clickable.
 */
@Composable
public fun AmStatCard(
    value: String,
    label: String,
    icon: IconKind,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
) {
    val colors = AmTheme.colors
    val shape = AmTheme.shape
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(colors.surfaces.panel.toColor(), shape.corner)
                .border(shape.borderWidth, colors.surfaces.border.toColor(), shape.corner)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIconTile(icon, tone, null, Modifier.size(42.dp))
        Column {
            AmText(value, role = TextRole.TITLE)
            AmText(label, role = TextRole.CAPTION, color = colors.content.muted.toColor())
        }
    }
}
