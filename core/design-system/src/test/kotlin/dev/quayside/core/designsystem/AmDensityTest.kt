package dev.quayside.core.designsystem

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The measures of a list row per density (docs/08, plan AM-803); every field is pinned, not only the whole value. */
class AmDensityTest {
    @ParameterizedTest(name = "{0} has tile {1}, padding {2}, two lines {3}")
    @MethodSource("measures")
    fun `list metrics of a density`(
        density: AmDensity,
        tile: Float,
        vertical: Float,
        twoLines: Boolean,
    ) {
        val metrics = density.listMetrics()

        metrics.tile shouldBe tile.dp
        metrics.vertical shouldBe vertical.dp
        metrics.twoLines shouldBe twoLines
    }

    @Test
    fun `comfortable keeps the measures of AM-809`() {
        AmDensity.COMFORTABLE.listMetrics() shouldBe ListMetrics(tile = 38.dp, vertical = 8.dp, twoLines = true)
    }

    @Test
    fun `compact is tighter than comfortable in every measure`() {
        val compact = AmDensity.COMPACT.listMetrics()
        val comfortable = AmDensity.COMFORTABLE.listMetrics()

        (compact.tile < comfortable.tile) shouldBe true
        (compact.vertical < comfortable.vertical) shouldBe true
    }

    companion object {
        @JvmStatic
        fun measures(): Stream<Arguments> =
            Stream.of(
                Arguments.of(AmDensity.COMFORTABLE, 38f, 8f, true),
                Arguments.of(AmDensity.COMPACT, 28f, 4f, false),
            )
    }
}
