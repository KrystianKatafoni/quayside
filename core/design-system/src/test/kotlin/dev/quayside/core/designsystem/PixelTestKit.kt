package dev.quayside.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
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

/** Pixels per dp in [Scaled] content: strokes of 16-20 dp icons then have fully covered pixels. */
internal const val PIXEL_SCALE = 4

/** Pixels of [dp] in [Scaled] content. */
internal fun px(dp: Int): Int = dp * PIXEL_SCALE

/** Lays out [content] at 4 pixels per dp, so pixel tests can read exact colours inside thin strokes. */
@Composable
internal fun Scaled(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(PIXEL_SCALE.toFloat()), content = content)
}

/** Coordinates (x, y) of every pixel with the given ARGB. */
internal fun PixelMap.positionsOf(argb: Int): List<Pair<Int, Int>> {
    val found = ArrayList<Pair<Int, Int>>()
    for (y in 0 until height) for (x in 0 until width) if (this[x, y].toArgb() == argb) found.add(x to y)
    return found
}

/** Requests focus for the node. */
internal fun SemanticsNodeInteraction.focus() = performSemanticsAction(SemanticsActions.RequestFocus)

/** Presses and releases [key] in the window and waits until the UI is idle. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.press(key: Key) {
    onRoot().performKeyInput {
        keyDown(key)
        keyUp(key)
    }
    waitForIdle()
}
