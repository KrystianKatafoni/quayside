package dev.quayside.core.designsystem

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import dev.quayside.core.designtokens.Rgba

/** Pixels of the node as rendered by the test window (software renderer, no screen). */
internal fun SemanticsNodeInteraction.pixels(): PixelMap = captureToImage().toPixelMap()

/** Opaque ARGB of a token, as [PixelMap] reports a pixel (`Color.toArgb`). */
internal fun Rgba.argb32(): Int = toColor().toArgb()

/** Every distinct ARGB value that occurs in the node. */
internal fun PixelMap.distinctColors(): Set<Int> {
    val found = HashSet<Int>()
    for (y in 0 until height) for (x in 0 until width) found.add(this[x, y].toArgb())
    return found
}

/** ARGB of the pixel at ([x], [y]). */
internal fun PixelMap.argbAt(
    x: Int,
    y: Int,
): Int = this[x, y].toArgb()
