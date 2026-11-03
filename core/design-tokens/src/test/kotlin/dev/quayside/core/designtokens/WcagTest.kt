package dev.quayside.core.designtokens

import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WcagTest {
    @ParameterizedTest(name = "luminance of {0} is {1}")
    @CsvSource("0xFFFFFF, 1.0", "0x000000, 0.0", "0x808080, 0.2159", "0x0A0A0A, 0.00304")
    fun `luminance follows the WCAG formula including the linear branch`(
        value: String,
        expected: Double,
    ) {
        Wcag.luminance(rgb(java.lang.Long.decode(value))) shouldBe (expected plusOrMinus LUMINANCE_TOLERANCE)
    }

    @ParameterizedTest(name = "contrast of {0} on {1} is {2}")
    @CsvSource(
        "0x000000, 0xFFFFFF, 21.0",
        "0xFFFFFF, 0xFFFFFF, 1.0",
        "0x767676, 0xFFFFFF, 4.54",
        "0x777777, 0xFFFFFF, 4.48",
    )
    fun `contrast gives the WCAG ratio`(
        foreground: String,
        background: String,
        expected: Double,
    ) {
        val ratio = Wcag.contrast(rgb(java.lang.Long.decode(foreground)), rgb(java.lang.Long.decode(background)))

        ratio shouldBe (expected plusOrMinus RATIO_TOLERANCE)
    }

    @Test
    fun `the text threshold separates 767676 from 777777 on white`() {
        val white = rgb(0xFFFFFF)

        (Wcag.contrast(rgb(0x767676), white) >= Wcag.TEXT_MIN) shouldBe true
        (Wcag.contrast(rgb(0x777777), white) < Wcag.TEXT_MIN) shouldBe true
    }

    @Test
    fun `contrast is symmetric for opaque colours`() {
        val a = rgb(0x4338CA)
        val b = rgb(0xF4F6F8)

        Wcag.contrast(a, b) shouldBe Wcag.contrast(b, a)
    }

    @Test
    fun `translucent foreground is composited over the background before measuring`() {
        val background = rgb(0xF3F0FF)
        val translucent = rgb(0xFFFFFF).withAlpha(60)

        Wcag.contrast(translucent, background) shouldBe Wcag.contrast(rgb(0xFAF9FF), background)
    }

    @ParameterizedTest(name = "ink for {0} is black: {1}")
    @CsvSource("0xFFD400, true", "0x4338CA, false")
    fun `inkFor picks the more contrasting of black and white`(
        background: String,
        black: Boolean,
    ) {
        val ink = Wcag.inkFor(rgb(java.lang.Long.decode(background)))

        ink shouldBe (if (black) Rgba.BLACK else Rgba.WHITE)
    }

    private companion object {
        const val LUMINANCE_TOLERANCE = 0.00005
        const val RATIO_TOLERANCE = 0.01
    }
}
