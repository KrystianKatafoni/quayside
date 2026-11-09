package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.Rgba
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.stream.Stream
import javax.swing.SwingUtilities

/**
 * Pixels of a real [ThemedScrollBar] painted into a [BufferedImage] 10 x 200 (docs/plans/AM-810.md): no window, so it
 * runs headless too. Colours differ in every channel and from any look and feel, so a swapped role fails.
 * Pixels are read only from the middle of the thumb and of the track, never from antialiased corners.
 */
class ThemedScrollBarTest {
    private fun bar(
        min: Int,
        extent: Int,
        max: Int,
        value: Int = 0,
    ): ThemedScrollBar {
        val bar = ThemedScrollBar(COLORS)
        bar.setSize(WIDTH, HEIGHT)
        bar.model.setRangeProperties(value, extent, min, max, false)
        bar.doLayout()
        return bar
    }

    private fun ThemedScrollBar.paintToImage(): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun BufferedImage.rgbAt(
        x: Int,
        y: Int,
    ): Int = getRGB(x, y) and RGB_MASK

    private val track = TRACK_RGB
    private val thumb = THUMB_RGB

    @Test
    fun `track is painted in track colour`() {
        val image = bar(min = -1000, extent = 40, max = 40).paintToImage()

        image.rgbAt(MIDDLE_X, 50) shouldBe track
        image.rgbAt(MIDDLE_X, 100) shouldBe track
    }

    @Test
    fun `thumb is painted in thumb colour`() {
        val image = bar(min = -1000, extent = 40, max = 40).paintToImage()

        image.rgbAt(MIDDLE_X, 188) shouldBe thumb
        image.rgbAt(MIDDLE_X, 50) shouldBe track
    }

    @Test
    fun `thumb fills the track when there is no scrollback`() {
        val image = bar(min = 0, extent = 40, max = 40).paintToImage()

        image.rgbAt(MIDDLE_X, HEIGHT / 4) shouldBe thumb
        image.rgbAt(MIDDLE_X, HEIGHT * 3 / 4) shouldBe thumb
    }

    @Test
    fun `thumb keeps 24 px with long scrollback`() {
        val image = bar(min = -100_000, extent = 40, max = 40).paintToImage()

        val painted = (0 until HEIGHT).count { image.rgbAt(MIDDLE_X, it) == thumb }

        // 24 px thumb with a 2 px inset on the track edge: never the 8 px of BasicScrollBarUI.
        painted.shouldBeBetween(MIN_PAINTED, MIN_THUMB)
    }

    @Test
    fun `thumb is inset 2 px from the track edge`() {
        val image = bar(min = -1000, extent = 40, max = 40).paintToImage()

        image.rgbAt(1, 188) shouldBe track
        image.rgbAt(2, 188) shouldBe thumb
    }

    @Test
    fun `click in the track above the thumb scrolls a page`() {
        val bar = bar(min = -1000, extent = 40, max = 40)
        val before = bar.value

        val event = MouseEvent(bar, MouseEvent.MOUSE_PRESSED, 0L, 0, MIDDLE_X, 20, 1, false, MouseEvent.BUTTON1)
        bar.mouseListeners.forEach { it.mousePressed(event) }
        bar.mouseListeners.forEach { it.mouseReleased(event) }

        (bar.value < before) shouldBe true
    }

    @Test
    fun `no arrow buttons are painted`() {
        val image = bar(min = -1000, extent = 40, max = 40).paintToImage()

        listOf(1, HEIGHT - 1).forEach { y ->
            (image.rgbAt(MIDDLE_X, y) in setOf(track, thumb)) shouldBe true
        }
    }

    @ParameterizedTest(name = "refresh through the whole component tree = {0}")
    @MethodSource("uiRefreshes")
    fun `update ui keeps themed colours`(componentTree: Boolean) {
        val bar = bar(min = -1000, extent = 40, max = 40)

        if (componentTree) SwingUtilities.updateComponentTreeUI(bar) else bar.updateUI()
        bar.setSize(WIDTH, HEIGHT)
        bar.doLayout()
        val image = bar.paintToImage()

        image.rgbAt(MIDDLE_X, 188) shouldBe thumb
        image.rgbAt(MIDDLE_X, 50) shouldBe track
    }

    @Test
    fun `preferred width is 10`() {
        bar(min = -1000, extent = 40, max = 40).preferredSize.width shouldBe WIDTH
    }

    @ParameterizedTest(name = "{0} gives {1}")
    @MethodSource("tokens")
    fun `maps red green and blue of the token`(
        argb: Long,
        expected: Color,
    ) {
        Rgba(argb).toAwtColor() shouldBe expected
    }

    companion object {
        private const val WIDTH = 10
        private const val HEIGHT = 200
        private const val MIDDLE_X = 5
        private const val RGB_MASK = 0xFFFFFF
        private const val MIN_THUMB = 24
        private const val MIN_PAINTED = 20
        private const val TRACK_RGB = 0x405060
        private const val THUMB_RGB = 0xC08040
        private val COLORS =
            ScrollbarColors(background = Rgba(0xFF102030), track = Rgba(0xFF405060), thumb = Rgba(0xFFC08040))

        @JvmStatic
        fun uiRefreshes(): Stream<Arguments> = Stream.of(Arguments.of(false), Arguments.of(true))

        // Table 2 of the plan: Rgba to AWT, alpha dropped (the AWT colour is opaque).
        @JvmStatic
        fun tokens(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFF102030L, Color(16, 32, 48, 255)),
                Arguments.of(0x80E0D0C0L, Color(224, 208, 192, 255)),
            )
    }
}
