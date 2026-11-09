package dev.quayside.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import java.awt.image.BufferedImage

/**
 * The Quayside mark (lighthouse, ADR 0030) at [size]: below 32 dp the simplified drawing, from 32 dp the full one.
 * Fixed colours on an always dark tile: it ignores the theme and LocalTextColor. Never clickable or focusable.
 * [contentDescription] is read by accessibility; null = decorative, no semantics node. Main thread (Compose).
 *
 * @throws IllegalStateException when a logo resource is missing from the classpath (programmer error).
 */
@Composable
public fun AmLogo(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberAmLogoPainter(size),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/**
 * Painter of the mark drawing chosen for [size] (e.g. the window icon); remembered per drawing.
 *
 * @throws IllegalStateException when a logo resource is missing from the classpath (programmer error).
 */
@Composable
public fun rememberAmLogoPainter(size: Dp): Painter {
    val drawing = logoDrawingFor(size)
    return remember(drawing) { SvgPainter(svgDomOf(drawing)) }
}

/**
 * The full drawing rasterised to a square ARGB image of [sizePx] pixels (Dock icon). Callable off the UI thread.
 *
 * @throws IllegalArgumentException when [sizePx] is not positive (programmer error).
 * @throws IllegalStateException when a logo resource is missing from the classpath (programmer error).
 */
public fun amLogoImage(sizePx: Int): BufferedImage {
    require(sizePx > 0) { "sizePx must be positive" }
    Bitmap().use { bitmap ->
        bitmap.allocN32Pixels(sizePx, sizePx)
        Canvas(bitmap).use { canvas ->
            canvas.clear(Color.TRANSPARENT)
            svgDomOf(LogoDrawing.FULL).use { dom -> renderSvg(dom, canvas, sizePx.toFloat(), sizePx.toFloat()) }
        }
        return bitmap.asComposeImageBitmap().toAwtImage()
    }
}
