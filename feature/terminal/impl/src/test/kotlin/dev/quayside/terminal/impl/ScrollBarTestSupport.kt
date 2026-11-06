package dev.quayside.terminal.impl

import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage

private const val RGB_MASK = 0xFFFFFF
private const val BAR_WIDTH = 10
private const val BAR_HEIGHT = 300

/** All descendants of this component, depth first. Swing thread only. */
internal fun Component.descendants(): List<Component> =
    if (this is Container) components.toList().flatMap { listOf(it) + it.descendants() } else emptyList()

/** The themed scroll bar in this component tree, null when the tree has none. Swing thread only. */
internal fun Component.findThemedBar(): ThemedScrollBar? =
    descendants().filterIsInstance<ThemedScrollBar>().firstOrNull()

/**
 * RGB (without alpha) of the pixel in the middle of this bar painted into an image, no window needed; the bar is
 * resized (this changes the real bar) to at least 10 x 300 first. A bar whose model shows everything has the
 * thumb there. Swing thread only.
 */
internal fun ThemedScrollBar.centrePixel(): Int {
    setSize(width.coerceAtLeast(BAR_WIDTH), height.coerceAtLeast(BAR_HEIGHT))
    doLayout()
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        paint(g)
    } finally {
        g.dispose()
    }
    return image.getRGB(width / 2, height / 2) and RGB_MASK
}
