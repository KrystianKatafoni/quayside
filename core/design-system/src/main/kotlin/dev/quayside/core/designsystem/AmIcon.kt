package dev.quayside.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val GRID = 24f
private const val STROKE = 1.8f

/** Black line drawing of the icon; the colour comes from a tint filter, so one vector serves every colour. */
internal fun IconKind.toImageVector(): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = GRID.dp,
            defaultHeight = GRID.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
        ).addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

/**
 * [icon] at 18 dp (change with `Modifier.size`). [contentDescription] null = decorative, no semantics node.
 * [tint] null = the container colour (content.text by default), as AmText.
 */
@Composable
public fun AmIcon(
    icon: IconKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val color =
        tint ?: LocalTextColor.current ?: AmTheme.colors.content.text
            .toColor()
    Image(
        imageVector = remember(icon) { icon.toImageVector() },
        contentDescription = contentDescription,
        modifier = modifier.size(18.dp),
        colorFilter = ColorFilter.tint(color),
    )
}
