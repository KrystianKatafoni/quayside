package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** The 4.5:1 correction of status colours and muted text on the surfaces the screens draw them on (AM-803). */
class ReadableOnTest {
    @Test
    fun `a colour that meets 4_5 on every surface is kept`() {
        hex("#FFFFFF").readableOn(listOf(hex("#000000"), hex("#222222")), dark = true) shouldBe hex("#FFFFFF")
    }

    @Test
    fun `a dark theme colour is lightened toward white on the weakest surface`() {
        val surfaces = listOf(hex("#000000"), hex("#303030"))
        val result = hex("#707070").readableOn(surfaces, dark = true)

        result shouldNotBe hex("#707070")
        (Wcag.luminance(result) > Wcag.luminance(hex("#707070"))) shouldBe true
        surfaces.forEach { Wcag.contrast(result, it) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN }
    }

    @Test
    fun `a light theme colour is darkened toward black on the weakest surface`() {
        val surfaces = listOf(hex("#FFFFFF"), hex("#E0E0E0"))
        val result = hex("#909090").readableOn(surfaces, dark = false)

        (Wcag.luminance(result) < Wcag.luminance(hex("#909090"))) shouldBe true
        surfaces.forEach { Wcag.contrast(result, it) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN }
    }

    @Test
    fun `the hue is kept while the colour is corrected`() {
        // Amber on white is darkened; the hue (HSV, degrees) moves by less than 2
        val amber = hex("#F59E0B")
        val result: Rgba = amber.readableOn(listOf(hex("#FFFFFF")), dark = false)

        result shouldNotBe amber
        (abs(hueOf(result) - hueOf(amber)) < 2.0) shouldBe true
    }

    private fun hueOf(color: Rgba): Double {
        val hsv = FloatArray(3)
        java.awt.Color.RGBtoHSB(color.red, color.green, color.blue, hsv)
        return hsv[0] * 360.0
    }
}
