package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Colour of "side" in the wordmark (decision 1 of AM-822, option A): the accent when it reads 4.5:1 on the panel as
 * drawn, else mixed toward the text colour in 5% steps. The data differ in bg, panel, text and accent on purpose.
 */
class WordmarkAccentTest {
    private fun tokens(
        bg: Rgba,
        panel: Rgba,
        text: Rgba,
        accent: Rgba,
    ): ColorTokens {
        val base = SampleTokens.colors()
        return base.copy(
            surfaces = base.surfaces.copy(bg = bg, panel = panel),
            content = base.content.copy(text = text, accent = accent),
        )
    }

    private fun drawnPanel(colors: ColorTokens): Rgba = colors.surfaces.panel.over(colors.surfaces.bg)

    private fun Rgba.contrastOn(panel: Rgba): Double = Wcag.contrast(this, panel)

    @Test
    fun `readable accent is kept`() {
        val colors = tokens(rgb(0x0B0A10), rgb(0x17141F), rgb(0xE8E5F2), rgb(0xA78BFA))

        colors.wordmarkAccent() shouldBe colors.content.accent
    }

    @Test
    fun `weak accent is mixed toward text until 4_5 to 1`() {
        val colors = tokens(rgb(0x0B0A10), rgb(0x100E17), rgb(0xE8E5F2), rgb(0x5B4FA0))
        val panel = drawnPanel(colors)
        val accent = colors.content.accent
        accent.contrastOn(panel) shouldBeLessThan Wcag.TEXT_MIN

        val result = colors.wordmarkAccent()

        result.contrastOn(panel) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        result shouldNotBe accent
        result shouldNotBe colors.content.text
        // the step before is still too weak: the smallest sufficient 5% step is taken, not a bigger one
        val step = (STEP..FULL).step(STEP).first { accent.mix(colors.content.text, it) == result }
        accent.mix(colors.content.text, step - STEP).contrastOn(panel) shouldBeLessThan Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "accent {0}")
    @ValueSource(longs = [0x202020, 0x5B4FA0, 0x7C6CD0, 0x303060])
    fun `result is the first 5 percent step that reads on the panel`(accentRgb: Long) {
        val colors = tokens(rgb(0x0B0A10), rgb(0x100E17), rgb(0xE8E5F2), rgb(accentRgb))
        val panel = drawnPanel(colors)
        val accent = colors.content.accent

        val expected =
            (0..FULL step STEP)
                .map { accent.mix(colors.content.text, it) }
                .first { it.contrastOn(panel) >= Wcag.TEXT_MIN }

        colors.wordmarkAccent() shouldBe expected
    }

    @Test
    fun `contrast is measured on the panel, not bg`() {
        val colors = tokens(rgb(0x000000), rgb(0x3A3A4A), rgb(0xF0F0FF), rgb(0xA0A0C0))
        colors.content.accent.contrastOn(colors.surfaces.bg) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        colors.content.accent.contrastOn(colors.surfaces.panel) shouldBeLessThan Wcag.TEXT_MIN

        val result = colors.wordmarkAccent()

        result shouldNotBe colors.content.accent
        result.contrastOn(colors.surfaces.panel) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @Test
    fun `translucent panel is measured over bg`() {
        val bg = rgb(0x000000)
        val opaque = tokens(bg, rgb(0x3A3A4A), rgb(0xF0F0FF), rgb(0xA0A0C0))
        val glass = opaque.copy(surfaces = opaque.surfaces.copy(panel = rgb(0x3A3A4A).withAlpha(HALF)))

        // over bg the glass panel is dark enough for the accent, the same colour as an opaque panel is not
        glass.wordmarkAccent() shouldBe glass.content.accent
        opaque.wordmarkAccent() shouldNotBe opaque.content.accent
    }

    private companion object {
        const val STEP = 5
        const val FULL = 100
        const val HALF = 50
    }
}
