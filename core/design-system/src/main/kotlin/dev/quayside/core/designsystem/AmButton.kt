package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val CONTENT_GAP = 6.dp
private val ICON_SIZE = 16.dp

/**
 * Button (Role.Button) with [label]; content centred when [modifier] widens it. Focusable (default): Space and Enter
 * click it while focused. A disabled button has a dim label, ignores clicks and is announced as disabled. See
 * [ButtonOptions] for the kind, icon and shortcut.
 */
@Composable
public fun AmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: ButtonOptions = ButtonOptions(),
) {
    val colors = buttonColors(options.kind, options.enabled, AmTheme.colors)
    val shape = AmTheme.shape
    val focus = if (options.focusable) Modifier.amFocusFrame() else Modifier.focusProperties { canFocus = false }
    val outline =
        colors.border?.let { Modifier.border(shape.borderWidth, it.toColor(), shape.corner) } ?: Modifier
    Row(
        modifier =
            modifier
                .then(focus)
                .clip(shape.corner)
                .background(colors.background.toColor())
                .then(outline)
                .clickable(enabled = options.enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = colors.label.toColor()
        options.icon?.let { AmIcon(it, null, Modifier.size(ICON_SIZE), ink) }
        AmText(label, color = ink)
        options.shortcut?.let { AmText(it, role = TextRole.CODE, color = ink) }
    }
}
