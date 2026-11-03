package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** One row of table 1 of the AM-805 plan. */
data class ContrastCase(
    val label: String,
    val source: String,
    val backgrounds: List<String>,
    val minimum: Double,
    val toward: Rgba,
    val expected: String,
) {
    override fun toString(): String = label
}

/** Table 1 of the AM-805 plan. */
class WithContrastTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `moves a colour toward the target until the weakest background passes`(case: ContrastCase) {
        hex(case.source).withContrast(case.backgrounds.map(::hex), case.minimum, case.toward) shouldBe
            hex(case.expected)
    }

    @Test
    fun `result is minimal on a dark background toward white`() {
        val source = hex("#6272A4")
        val background = hex("#44475A")

        val result = source.withContrast(listOf(background), 4.5, Rgba.WHITE)

        val step = (0..100).first { source.mix(Rgba.WHITE, it) == result }
        Wcag.contrast(result, background) shouldBeGreaterThanOrEqualTo 4.5
        (step > 0) shouldBe true
        Wcag.contrast(source.mix(Rgba.WHITE, step - 1), background) shouldBeLessThan 4.5
    }

    companion object {
        private val WHITE_PAGE = listOf("#FFFFFF")

        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): Stream<ContrastCase> =
            Stream.of(
                ContrastCase("one background, black", "#808080", WHITE_PAGE, 4.5, Rgba.BLACK, "#767676"),
                ContrastCase(
                    "worse background decides",
                    "#808080",
                    listOf("#FFFFFF", "#F0F0F0"),
                    4.5,
                    Rgba.BLACK,
                    "#6D6D6D",
                ),
                ContrastCase(
                    "order of backgrounds is irrelevant",
                    "#808080",
                    listOf("#F0F0F0", "#FFFFFF"),
                    4.5,
                    Rgba.BLACK,
                    "#6D6D6D",
                ),
                ContrastCase("already passing is unchanged", "#595959", WHITE_PAGE, 4.5, Rgba.BLACK, "#595959"),
                ContrastCase("toward white on black", "#404040", listOf("#000000"), 4.5, Rgba.WHITE, "#757575"),
                ContrastCase(
                    "toward white, darker background decides",
                    "#404040",
                    listOf("#000000", "#1A1A1A"),
                    4.5,
                    Rgba.WHITE,
                    "#838383",
                ),
                ContrastCase("unreachable returns black", "#808080", listOf("#808080"), 22.0, Rgba.BLACK, "#000000"),
                ContrastCase("unreachable returns white", "#336699", listOf("#336699"), 22.0, Rgba.WHITE, "#FFFFFF"),
            )
    }
}
