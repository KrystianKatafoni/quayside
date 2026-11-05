package dev.quayside.core.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** A row of the `openAt` table. */
internal data class OpenAtRow(
    val name: String,
    val selected: Int,
    val size: Int,
    val expected: Int,
) {
    override fun toString(): String = name
}

/** A row of the `dropdownStep` table. */
internal data class StepRow(
    val name: String,
    val at: Int,
    val by: Int,
    val size: Int,
    val expected: Int,
) {
    override fun toString(): String = name
}

/** A row of the `dropdownPosition` table; the anchor is the field in window coordinates. */
internal data class PositionRow(
    val name: String,
    val anchor: IntRect,
    val window: IntSize,
    val popup: IntSize,
    val gap: Int,
    val expected: IntOffset,
) {
    override fun toString(): String = name
}

/** The pure rules of the dropdown list: where the focus starts, how it moves and where the list is placed. */
internal class DropdownRulesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("openAtTable")
    fun `the open list focuses the selected option, else the first, else nothing`(row: OpenAtRow) {
        openAt(row.selected, row.size) shouldBe row.expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stepTable")
    fun `a step moves the focus by one and stops at the ends`(row: StepRow) {
        dropdownStep(row.at, row.by, row.size) shouldBe row.expected
    }

    @Test
    fun `three steps down from the first reach the fourth`() {
        val reached = (1..3).fold(0) { at, _ -> dropdownStep(at, 1, 4) }

        reached shouldBe 3
    }

    @Test
    fun `no single step from the first reaches the last`() {
        listOf(-1, 1).forEach { by -> (dropdownStep(0, by, 4) == 3) shouldBe false }
    }

    @Test
    fun `steps past the end stay on the end however many there are`() {
        val reached = (1..10).fold(0) { at, _ -> dropdownStep(at, 1, 4) }

        reached shouldBe 3
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionTable")
    fun `the list goes under the field, over it, or is kept inside the window`(row: PositionRow) {
        dropdownPosition(row.anchor, row.window, row.popup, row.gap) shouldBe row.expected
    }

    companion object {
        @JvmStatic
        fun openAtTable(): Stream<OpenAtRow> =
            Stream.of(
                OpenAtRow("first selected", selected = 0, size = 4, expected = 0),
                OpenAtRow("third selected", selected = 2, size = 4, expected = 2),
                OpenAtRow("none selected (-1)", selected = -1, size = 4, expected = 0),
                OpenAtRow("selected past the end", selected = 7, size = 4, expected = 0),
                OpenAtRow("empty list does not open", selected = 0, size = 0, expected = -1),
            )

        @JvmStatic
        fun stepTable(): Stream<StepRow> =
            Stream.of(
                StepRow("up from the first stays", at = 0, by = -1, size = 4, expected = 0),
                StepRow("down from the first", at = 0, by = 1, size = 4, expected = 1),
                StepRow("down from the last stays", at = 3, by = 1, size = 4, expected = 3),
                StepRow("up from the third", at = 2, by = -1, size = 4, expected = 1),
                StepRow("down in a list of one stays", at = 0, by = 1, size = 1, expected = 0),
            )

        private val WINDOW = IntSize(800, 600)
        private val POPUP = IntSize(200, 100)

        private fun field(
            left: Int,
            top: Int,
            width: Int = 200,
            height: Int = 30,
        ) = IntRect(left, top, left + width, top + height)

        @JvmStatic
        @Suppress("LongMethod")
        fun positionTable(): Stream<PositionRow> =
            Stream.of(
                PositionRow("room under: below, left aligned", field(50, 100), WINDOW, POPUP, 4, IntOffset(50, 134)),
                PositionRow("room under with another gap", field(70, 100), WINDOW, POPUP, 8, IntOffset(70, 138)),
                // bottom 496 + gap 4 + height 100 = 600: touches the window edge, still below
                PositionRow(
                    "popup ends exactly at the window edge",
                    field(50, 466),
                    WINDOW,
                    POPUP,
                    4,
                    IntOffset(50, 500),
                ),
                // one pixel lower: 601 does not fit under; over: 467 - 4 - 100 = 363
                PositionRow("one pixel short under: over", field(50, 467), WINDOW, POPUP, 4, IntOffset(50, 363)),
                PositionRow("no room under: over, other gap", field(90, 500), WINDOW, POPUP, 10, IntOffset(90, 390)),
                // window 150 high: under 94 + 100 > 150, over 60 - 4 - 100 < 0 -> under, cut to the bottom edge
                PositionRow(
                    "no room under or over: bottom edge",
                    field(30, 60),
                    IntSize(800, 150),
                    POPUP,
                    4,
                    IntOffset(30, 50),
                ),
                PositionRow("past the right edge: moved left", field(700, 100), WINDOW, POPUP, 4, IntOffset(600, 134)),
                PositionRow("past the left edge: x is 0", field(-30, 100), WINDOW, POPUP, 4, IntOffset(0, 134)),
                PositionRow(
                    "wider than the window: x is 0",
                    field(10, 100),
                    IntSize(100, 600),
                    POPUP,
                    4,
                    IntOffset(0, 134),
                ),
            )
    }
}
