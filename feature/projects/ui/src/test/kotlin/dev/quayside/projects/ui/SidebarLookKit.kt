package dev.quayside.projects.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.Rgba

/** Pixels per dp in [Scaled4]. */
internal const val SCALE = 4

/** Lays out [content] at 4 pixels per dp, so pixel tests can read thin strokes and arcs. */
@Composable
internal fun Scaled4(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(SCALE.toFloat()), content = content)
}

/** Opaque ARGB of a token as [PixelMap] reports it. */
internal fun Rgba.argb(): Int = toColor().toArgb()

/** Pixels of the node. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.pixelMap(): PixelMap = captureToImage().toPixelMap()

/** ARGB of the pixel at ([x], [y]). */
internal fun PixelMap.argbAt(
    x: Int,
    y: Int,
): Int = this[x, y].toArgb()

/** Index of the first pixel on the diagonal from the top left corner whose colour is not [container]; null if none. */
internal fun PixelMap.diagonalOnset(container: Int): Int? =
    (0 until minOf(width, height)).firstOrNull { this[it, it].toArgb() != container }

/** Colour of the first pixel going down the middle column from the top that is not [container]; null if none. */
internal fun PixelMap.topEdgeColor(container: Int): Int? =
    (0 until height).map { this[width / 2, it].toArgb() }.firstOrNull { it != container }

/** The text style a text node was laid out with. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.textStyle(): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
    checkNotNull(action) { "node has no text layout" }.invoke(results)
    return results.first().layoutInput.style
}

/** Matches the nodes of the subtree tagged [tag], the tagged node included. */
internal fun inTag(tag: String) = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))
