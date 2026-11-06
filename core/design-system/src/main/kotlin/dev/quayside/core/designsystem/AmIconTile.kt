package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val ICON_SHARE = 0.5f

/**
 * [icon] centred on the tone background (rounded with the tile radius, r+2), icon about half the tile; size via
 * [modifier]. [contentDescription] as in [AmIcon].
 */
@Composable
public fun AmIconTile(
    icon: IconKind,
    tone: StatusTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = chipColors(tone, AmTheme.colors)
    val described =
        if (contentDescription == null) {
            Modifier
        } else {
            Modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        }
    Box(
        modifier = modifier.then(described).background(colors.background.toColor(), AmTheme.shape.tile),
        contentAlignment = Alignment.Center,
    ) {
        AmIcon(icon, null, Modifier.fillMaxSize(ICON_SHARE), colors.mark.toColor())
    }
}
