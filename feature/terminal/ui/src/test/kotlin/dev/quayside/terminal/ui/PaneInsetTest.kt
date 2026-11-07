package dev.quayside.terminal.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.sqrt

/** Table 2 of the AM-816 plan: the square corners of a Swing widget must fit inside the rounded frame of the pane. */
class PaneInsetTest {
    @ParameterizedTest(name = "radius {0} dp gives {1} dp")
    @CsvSource("0,2", "4,2", "12,4", "20,6")
    fun `pane inset covers the corner`(
        radius: Int,
        expected: Int,
    ) {
        paneInset(radius.dp) shouldBe expected.dp
    }

    @Test
    fun `pane inset is never smaller than the corner needs`() {
        // radius * (1 - 1 / sqrt 2) is the distance at which a square corner meets the arc; at least 2 dp
        (0..48).forEach { radius ->
            val inset: Dp = paneInset(radius.dp)

            inset.value shouldBeGreaterThanOrEqualTo (radius * (1 - 1 / sqrt(2.0))).toFloat()
            inset.value shouldBeGreaterThanOrEqualTo 2f
        }
    }

    @Test
    fun `pane inset grows with the radius but not faster than the radius`() {
        (0..47).forEach { radius ->
            (paneInset((radius + 1).dp) >= paneInset(radius.dp)) shouldBe true
            // not a blanket margin: the inset stays small against the radius
            (paneInset((radius + 1).dp).value <= (radius + 1) / 2f + 2f) shouldBe true
        }
    }
}
