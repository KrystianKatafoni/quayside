package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Selectable row (row radius r+2): sel background, a check mark at the end and `selected` semantics when [selected]
 * (not colour only, NFR-6). Focusable; Enter and Space click it. Resets the default text colour of [AmText] to
 * content.text. A list of rows must be wrapped in `Modifier.selectableGroup()` (AM-803) so that the rows are
 * announced as one group.
 */
@Composable
public fun AmListRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val (background, textColor) = rowColors(selected, AmTheme.colors)
    CompositionLocalProvider(LocalTextColor provides null) {
        Row(
            modifier =
                modifier
                    .amFocusFrame()
                    .clip(AmTheme.shape.row)
                    .background(background.toColor())
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            if (selected) AmText("✓", color = textColor.toColor())
        }
    }
}
