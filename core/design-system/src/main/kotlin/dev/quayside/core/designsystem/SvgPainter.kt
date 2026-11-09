package dev.quayside.core.designsystem

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit

/** Bytes of each logo resource, read on first use and shared; every painter builds its own (not thread safe) DOM. */
private val RESOURCE_BYTES: Map<LogoDrawing, Lazy<ByteArray>> =
    LogoDrawing.entries.associateWith { drawing -> lazy { readResource(drawing) } }

private fun readResource(drawing: LogoDrawing): ByteArray {
    val stream = LogoDrawing::class.java.classLoader.getResourceAsStream(drawing.resource)
    return checkNotNull(stream) { "missing resource ${drawing.resource}" }.use { it.readBytes() }
}

/**
 * A new SVG DOM of [drawing], scaled to its container by the viewBox.
 *
 * @throws IllegalStateException when the resource is missing from the classpath (programmer error).
 */
internal fun svgDomOf(drawing: LogoDrawing): SVGDOM {
    val bytes = checkNotNull(RESOURCE_BYTES[drawing]).value
    return SVGDOM(Data.makeFromBytes(bytes))
}

/** Renders [dom] on [canvas] so that its viewBox fills [width] x [height] pixels. */
internal fun renderSvg(
    dom: SVGDOM,
    canvas: Canvas,
    width: Float,
    height: Float,
) {
    dom.root?.let { root ->
        root.width = SVGLength(FULL_PERCENT, SVGLengthUnit.PERCENTAGE)
        root.height = SVGLength(FULL_PERCENT, SVGLengthUnit.PERCENTAGE)
    }
    dom.setContainerSize(width, height)
    dom.render(canvas)
}

/** Painter of one SVG on the Skia canvas (ADR 0030): fills the draw area, no intrinsic size, fixed colours. */
internal class SvgPainter(
    private val dom: SVGDOM,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas -> renderSvg(dom, canvas.skiaCanvas, size.width, size.height) }
    }
}

private const val FULL_PERCENT = 100f
