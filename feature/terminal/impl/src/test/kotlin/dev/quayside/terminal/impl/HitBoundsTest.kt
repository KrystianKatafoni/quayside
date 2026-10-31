package dev.quayside.terminal.impl

import dev.quayside.terminal.api.SearchMatch
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.Rectangle

/** The pixel box of a hit (plan AM-504, decision 7); pure arithmetic, no display. */
class HitBoundsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `hit box follows the cell grid`(
        @Suppress("UNUSED_PARAMETER") name: String,
        hit: SearchMatch,
        scroll: Scroll,
        expected: Rectangle,
    ) {
        hitBounds(hit, scroll.history, scroll.origin, insetX = 3, cell = Dimension(8, 16)) shouldBe expected
    }

    /** Scrollback line count and vertical scroll origin of the panel. */
    data class Scroll(
        val history: Int,
        val origin: Int,
    )

    companion object {
        private fun case(
            name: String,
            hit: SearchMatch,
            scroll: Scroll,
            expected: Rectangle,
        ): Arguments = Arguments.of(name, hit, scroll, expected)

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                case("first row, first column", SearchMatch(0, 0, 3), Scroll(0, 0), Rectangle(3, 0, 24, 16)),
                case("column offset and width", SearchMatch(2, 5, 9), Scroll(0, 0), Rectangle(43, 32, 32, 16)),
                case("history shifts the row up", SearchMatch(30, 0, 1), Scroll(30, 0), Rectangle(3, 0, 8, 16)),
                case("scrolled to the top", SearchMatch(4, 1, 2), Scroll(30, -30), Rectangle(11, 64, 8, 16)),
                case("row above the view", SearchMatch(10, 0, 2), Scroll(30, -5), Rectangle(3, -240, 16, 16)),
            )
    }
}
