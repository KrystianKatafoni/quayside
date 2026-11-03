package dev.quayside.core.designtokens

import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ColorMathTest {
    @ParameterizedTest(name = "hue of {0} is {1}")
    @CsvSource(
        "0xFF0000, 0.0",
        "0x00FF00, 120.0",
        "0x0000FF, 240.0",
        "0xFF00FF, 300.0",
        "0xB45309, 25.96",
        "0xEB6F92, 343.06",
    )
    fun `hue is the HSL hue in degrees`(
        color: String,
        expected: Double,
    ) {
        rgb(java.lang.Long.decode(color)).hue() shouldBe (expected plusOrMinus HUE_TOLERANCE)
    }

    @Test
    fun `hue of a grey is null`() {
        rgb(0x808080).hue().shouldBeNull()
    }

    @ParameterizedTest(name = "hue distance of {0} and {1} is {2}")
    @CsvSource("0xFF0000, 0xFF00FF, 60.0", "0xEBBCBA, 0xEB6F92, 19.4")
    fun `hueDistance is the shortest way around the circle`(
        a: String,
        b: String,
        expected: Double,
    ) {
        val distance = hueDistance(rgb(java.lang.Long.decode(a)), rgb(java.lang.Long.decode(b)))

        distance shouldBe (expected plusOrMinus DISTANCE_TOLERANCE)
    }

    @Test
    fun `hueDistance with a grey is null in both orders`() {
        hueDistance(rgb(0xFF0000), rgb(0x808080)).shouldBeNull()
        hueDistance(rgb(0x808080), rgb(0xFF0000)).shouldBeNull()
    }

    @ParameterizedTest(name = "mix of black and white at {0} percent is {1}")
    @CsvSource("0, 0x000000", "50, 0x808080", "100, 0xFFFFFF")
    fun `mix blends channels and rounds half up`(
        percent: Int,
        expected: String,
    ) {
        Rgba.BLACK.mix(Rgba.WHITE, percent) shouldBe rgb(java.lang.Long.decode(expected))
    }

    @Test
    fun `mix keeps the alpha of the receiver`() {
        val translucent = rgb(0x000000).withAlpha(40)

        translucent.mix(Rgba.WHITE, 100).alpha shouldBe translucent.alpha
    }

    @Test
    fun `over composites a translucent colour on an opaque background`() {
        val result = rgb(0xFFFFFF).withAlpha(60).over(rgb(0xF3F0FF))

        result shouldBe rgb(0xFAF9FF)
    }

    @Test
    fun `over an opaque colour changes nothing`() {
        rgb(0x123456).over(rgb(0xF3F0FF)) shouldBe rgb(0x123456)
    }

    @Test
    fun `over a translucent background is rejected`() {
        shouldThrow<IllegalArgumentException> { rgb(0xFFFFFF).over(rgb(0x000000).withAlpha(50)) }
    }

    @ParameterizedTest(name = "mix at {0} percent is rejected")
    @CsvSource("-1", "101")
    fun `mix rejects percent outside 0 to 100`(percent: Int) {
        shouldThrow<IllegalArgumentException> { Rgba.BLACK.mix(Rgba.WHITE, percent) }
    }

    @ParameterizedTest(name = "withAlpha at {0} percent is rejected")
    @CsvSource("-1", "101")
    fun `withAlpha rejects percent outside 0 to 100`(percent: Int) {
        shouldThrow<IllegalArgumentException> { Rgba.BLACK.withAlpha(percent) }
    }

    @Test
    fun `luminance and contrast reject a translucent background`() {
        val translucent = rgb(0x000000).withAlpha(50)

        shouldThrow<IllegalArgumentException> { Wcag.luminance(translucent) }
        shouldThrow<IllegalArgumentException> { Wcag.contrast(Rgba.WHITE, translucent) }
    }

    private companion object {
        const val HUE_TOLERANCE = 0.01
        const val DISTANCE_TOLERANCE = 0.05
    }
}
