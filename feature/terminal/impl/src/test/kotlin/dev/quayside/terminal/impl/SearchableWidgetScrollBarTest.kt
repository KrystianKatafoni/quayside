package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

/**
 * The scroll bar of the real [SearchableWidget] (docs/plans/AM-810.md, JediTerm `createScrollBar()`); needs a display
 * like the other widget tests. The widget is built on the event thread, with no PTY.
 */
class SearchableWidgetScrollBarTest {
    @BeforeEach
    fun setUp() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
    }

    private fun <T> onSwing(block: () -> T): T {
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(block) }
        return (outcome ?: error("block did not run")).getOrThrow()
    }

    private fun settings() = TerminalWidgetSettings(TerminalSettings(), COLORS) { true }

    @Test
    fun `widget scroll bar is themed`() {
        val bar = onSwing { SearchableWidget(settings()).findThemedBar() != null }

        bar shouldBe true
    }

    @Test
    fun `fresh widget shows thumb over the whole track`() {
        val pixel =
            onSwing {
                val widget = SearchableWidget(settings())
                widget.setSize(400, 300)
                widget.validate()
                widget.findThemedBar()?.centrePixel()
            }

        pixel shouldBe (
            settings()
                .scrollbar.thumb
                .toAwtColor()
                .rgb and RGB_MASK
        )
    }

    private companion object {
        const val RGB_MASK = 0xFFFFFF
        val COLORS = TerminalColors(termBg = Rgba(0xFF102030), termText = Rgba(0xFFE0D0C0))
    }
}
