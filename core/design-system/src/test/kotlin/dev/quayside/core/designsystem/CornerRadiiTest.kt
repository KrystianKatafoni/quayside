package dev.quayside.core.designsystem

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Table 1 of the AM-816 plan: radii derived from the token radius r (row r+2, card r+4, tile r+2; 0 stays 0). */
class CornerRadiiTest {
    @ParameterizedTest(name = "r {0}")
    @MethodSource("radii")
    fun `radii follow the token radius`(
        radius: Int,
        row: Int,
        card: Int,
        tile: Int,
    ) {
        val radii = cornerRadii(radius)

        // Each field on its own, so that a swapped or shared radius fails with a clear field
        radii.row shouldBe row.dp
        radii.card shouldBe card.dp
        radii.tile shouldBe tile.dp
    }

    @Test
    fun `zero radius keeps every corner square`() {
        cornerRadii(0) shouldBe CornerRadii(0.dp, 0.dp, 0.dp)
    }

    companion object {
        /** r, row, card, tile: the rows of table 1 (0 / 2 / 8 plex / 16) and an odd radius. */
        @JvmStatic
        fun radii(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0, 0, 0, 0),
                Arguments.of(2, 4, 6, 4),
                Arguments.of(5, 7, 9, 7),
                Arguments.of(8, 10, 12, 10),
                Arguments.of(16, 18, 20, 18),
            )
    }
}
