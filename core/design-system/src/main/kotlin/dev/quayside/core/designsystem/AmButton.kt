package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Focusable button (Role.Button); Space and Enter click it while focused. A disabled button has a dim label, ignores
 * clicks and is announced as disabled.
 */
@Composable
public fun AmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
) {
    val (background, labelColor) = buttonColors(kind, enabled, AmTheme.colors)
    val shape = AmTheme.shape
    val frame =
        if (kind == ButtonKind.SECONDARY) {
            Modifier.border(
                shape.borderWidth,
                buttonBorder(AmTheme.colors).toColor(),
                shape.corner,
            )
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .amFocusFrame()
                .clip(shape.corner)
                .background(background.toColor())
                .then(frame)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        AmText(label, color = labelColor.toColor())
    }
}
