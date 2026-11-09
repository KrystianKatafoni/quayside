package dev.quayside.core.designsystem

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The mark drawing follows the size in dp (plan AM-822, table 1; ADR 0030). */
internal class LogoDrawingTest {
    @ParameterizedTest(name = "{0} dp draws {1}")
    @CsvSource("16,SMALL", "20,SMALL", "21,SMALL", "31,SMALL", "32,FULL", "128,FULL")
    fun `drawing follows the size`(
        dp: Int,
        expected: LogoDrawing,
    ) {
        logoDrawingFor(dp.dp) shouldBe expected
    }

    @Test
    fun `fractional size below 32 dp draws the small drawing`() {
        logoDrawingFor(31.9.dp) shouldBe LogoDrawing.SMALL
    }

    @Test
    fun `the two drawings are two different resources`() {
        LogoDrawing.SMALL.resource shouldNotBe LogoDrawing.FULL.resource
    }
}
