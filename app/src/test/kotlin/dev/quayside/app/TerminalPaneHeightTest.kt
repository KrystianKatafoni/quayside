package dev.quayside.app

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The pure height rules of the resizable terminal pane (FR-T2, plan AM-819); dp are plain numbers in the tables. */
class TerminalPaneHeightTest {
    @ParameterizedTest(name = "{3}")
    @MethodSource("heights")
    fun `paneHeight limits the remembered height to the space of the window`(
        preferred: Float,
        available: Float,
        expected: Float,
        @Suppress("UNUSED_PARAMETER") name: String,
    ) {
        paneHeight(preferred.dp, available.dp) shouldBe expected.dp
    }

    @Test
    fun `the limits are 120 dp for the terminal, 160 dp for Mission Control and 320 dp by default`() {
        MIN_TERMINAL_HEIGHT shouldBe 120.dp
        MIN_MISSION_HEIGHT shouldBe 160.dp
        DEFAULT_TERMINAL_HEIGHT shouldBe 320.dp
    }

    @Test
    fun `drag up grows the terminal`() {
        afterDrag(shown = 320.dp, delta = (-100).dp, available = 600.dp) shouldBe 420.dp
    }

    @Test
    fun `drag down shrinks the terminal`() {
        afterDrag(shown = 320.dp, delta = 100.dp, available = 600.dp) shouldBe 220.dp
    }

    @Test
    fun `drag past Mission Control minimum stops at it`() {
        afterDrag(shown = 320.dp, delta = (-1000).dp, available = 600.dp) shouldBe 440.dp
    }

    @Test
    fun `drag past terminal minimum stops at it`() {
        afterDrag(shown = 320.dp, delta = 1000.dp, available = 600.dp) shouldBe 120.dp
    }

    @Test
    fun `drag in a window too small for both minimums keeps the terminal minimum`() {
        afterDrag(shown = 120.dp, delta = (-50).dp, available = 250.dp) shouldBe 120.dp
    }

    companion object {
        @JvmStatic
        fun heights(): Stream<Arguments> =
            Stream.of(
                arguments(320f, 600f, 320f, "fits"),
                arguments(50f, 600f, 120f, "below the terminal minimum"),
                arguments(120f, 600f, 120f, "at the terminal minimum"),
                arguments(440f, 600f, 440f, "at the maximum (600 - 160)"),
                arguments(900f, 600f, 440f, "above the maximum"),
                arguments(320f, 250f, 120f, "both minimums do not fit, the terminal wins"),
                arguments(320f, 100f, 100f, "less space than the terminal minimum"),
                arguments(320f, 0f, 0f, "no space"),
            )
    }
}
