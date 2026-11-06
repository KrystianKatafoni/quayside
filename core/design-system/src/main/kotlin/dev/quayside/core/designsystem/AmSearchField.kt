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

private val FIELD_GAP = 8.dp
private val FIELD_ICON = 16.dp

/**
 * Field-looking button (Role.Button): SEARCH icon, [placeholder] (muted) and [keyCap] (an [AmKeyCap], none when null)
 * on a raised field with the theme border. It is not a text field and never takes focus, so Space and Enter go on to
 * the window; the width comes from [modifier].
 */
@Composable
public fun AmSearchField(
    placeholder: String,
    keyCap: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AmTheme.colors
    val shape = AmTheme.shape
    val muted = colors.content.muted.toColor()
    Row(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .clip(shape.corner)
                .background(colors.surfaces.raised.toColor())
                .border(shape.borderWidth, colors.surfaces.border2.toColor(), shape.corner)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(IconKind.SEARCH, null, Modifier.size(FIELD_ICON), muted)
        AmText(placeholder, modifier = Modifier.weight(1f), color = muted)
        keyCap?.let { AmKeyCap(it) }
    }
}
