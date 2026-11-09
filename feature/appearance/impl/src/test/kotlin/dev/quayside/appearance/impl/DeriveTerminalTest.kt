package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Terminal colours of a theme (AM-811, ADR 0027). Expected hex values are computed by hand (mix: round(from + (to -
 * from) * p / 100), WCAG 2.x contrast), not by the code under test; the base palette is pinned from `ColorPaletteImpl`
 * of JediTerm 3.76.
 */
class DeriveTerminalTest {
    private val xterm =
        listOf(
            "#000000",
            "#CD0000",
            "#00CD00",
            "#CDCD00",
            "#1E90FF",
            "#CD00CD",
            "#00CDCD",
            "#E5E5E5",
            "#4C4C4C",
            "#FF0000",
            "#00FF00",
            "#FFFF00",
            "#4682B4",
            "#FF00FF",
            "#00FFFF",
            "#FFFFFF",
        )

    private fun derive(
        bg: String,
        dark: Boolean,
        text: String = "#3A3832",
    ): AnsiColors = deriveTerminal(hex(bg), hex(text), dark).ansi

    @Test
    fun `base palette is the xterm palette of JediTerm`() {
        XTERM_BASE.colors shouldBe xterm.map { hex(it) }
    }

    @Test
    fun `terminal uses bg and text in a light theme`() {
        val terminal = deriveTerminal(hex("#F4F6F8"), hex("#2D3748"), dark = false)

        terminal.termBg shouldBe hex("#F4F6F8")
        terminal.termText shouldBe hex("#2D3748")
    }

    @Test
    fun `terminal uses bg and text in a dark theme`() {
        val terminal = deriveTerminal(hex("#0B0A10"), hex("#E8E5F2"), dark = true)

        terminal.termBg shouldBe hex("#0B0A10")
        terminal.termText shouldBe hex("#E8E5F2")
    }

    @ParameterizedTest(name = "{0} {1}: index {2} is {3}")
    @MethodSource("pinned")
    fun `colour is corrected to the pinned value`(
        bg: String,
        dark: Boolean,
        index: Int,
        expected: String,
    ) {
        derive(bg, dark)[index] shouldBe hex(expected)
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("modes")
    fun `colour that already passes stays unchanged`(
        bg: String,
        dark: Boolean,
    ) {
        val ansi = derive(bg, dark)
        val passing = (0 until AnsiColors.SIZE).filter { Wcag.contrast(hex(xterm[it]), hex(bg)) >= Wcag.TEXT_MIN }

        (passing.size >= 2) shouldBe true
        passing.forEach { ansi[it] shouldBe hex(xterm[it]) }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("modes")
    fun `corrected colour is the smallest mix that passes`(
        bg: String,
        dark: Boolean,
    ) {
        val ansi = derive(bg, dark)
        val toward = if (dark) Rgba.WHITE else Rgba.BLACK
        val corrected = (0 until AnsiColors.SIZE).filter { ansi[it] != hex(xterm[it]) }

        (corrected.size >= 2) shouldBe true
        corrected.forEach { index ->
            val source = hex(xterm[index])
            val percent = (1..100).first { source.mix(toward, it) == ansi[index] }
            withClue("index $index") {
                Wcag.contrast(ansi[index], hex(bg)) shouldBeGreaterThanOrEqualTo Wcag.TEXT_MIN
                Wcag.contrast(source.mix(toward, percent - 1), hex(bg)) shouldBeLessThan Wcag.TEXT_MIN
            }
        }
    }

    @Test
    fun `background colour of the mode is not corrected`() {
        // Without the exception index 0 on #0B0A10 would become #7A7A7A and index 15 on #FFFFFF would become #757575.
        derive("#0B0A10", dark = true)[0] shouldBe hex("#000000")
        derive("#FFFFFF", dark = false)[15] shouldBe hex("#FFFFFF")
        derive("#F4F6F8", dark = false)[15] shouldBe hex("#FFFFFF")
    }

    @Test
    fun `the other end of the palette is corrected like any colour`() {
        // Index 15 on a dark theme and index 0 on a light one are ordinary colours of the palette.
        Wcag.contrast(derive("#0B0A10", dark = true)[15], hex("#0B0A10")) shouldBeGreaterThanOrEqualTo Wcag.TEXT_MIN
        Wcag.contrast(derive("#F4F6F8", dark = false)[0], hex("#F4F6F8")) shouldBeGreaterThanOrEqualTo Wcag.TEXT_MIN
    }

    @Test
    fun `dark flag decides the direction`() {
        // Grey #767676 is 4.5 to 1 on both white and black: yellow #CDCD00 goes to near black or near white.
        derive("#767676", dark = false)[3] shouldBe hex("#040400")
        derive("#767676", dark = true)[3] shouldBe hex("#FFFFFC")
    }

    @Test
    fun `rejects a translucent bg`() {
        val translucent = hex("#FFFFFF").withAlpha(50)

        shouldThrow<IllegalArgumentException> { deriveTerminal(translucent, hex("#000000"), dark = false) }
        shouldThrow<IllegalArgumentException> { deriveTerminal(translucent, hex("#FFFFFF"), dark = true) }
    }

    @Test
    fun `text does not influence the palette`() {
        derive("#F4F6F8", dark = false, text = "#2D3748") shouldBe derive("#F4F6F8", dark = false, text = "#000000")
    }

    companion object {
        @JvmStatic
        fun modes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("#FFFFFF", false),
                Arguments.of("#F4F6F8", false),
                Arguments.of("#0B0A10", true),
                Arguments.of("#1E242C", true),
            )

        /** bg, dark, xterm index, expected (see the table 1 of the plan; p from the smallest passing mix). */
        @JvmStatic
        fun pinned(): Stream<Arguments> =
            Stream.of(
                // white, light: yellow 1.7 to 1 -> p 41 toward black; red 5.8 to 1 unchanged; white is the mode bg
                Arguments.of("#FFFFFF", false, 3, "#797900"),
                Arguments.of("#FFFFFF", false, 1, "#CD0000"),
                Arguments.of("#FFFFFF", false, 15, "#FFFFFF"),
                Arguments.of("#FFFFFF", false, 2, "#008900"),
                Arguments.of("#FFFFFF", false, 7, "#757575"),
                Arguments.of("#FFFFFF", false, 0, "#000000"),
                // plex bg, dark: red 3.4 to 1 -> p 26 toward white
                Arguments.of("#0B0A10", true, 1, "#DA4242"),
                Arguments.of("#0B0A10", true, 0, "#000000"),
                Arguments.of("#0B0A10", true, 15, "#FFFFFF"),
                Arguments.of("#0B0A10", true, 8, "#797979"),
                Arguments.of("#0B0A10", true, 5, "#D217D2"),
                Arguments.of("#0B0A10", true, 2, "#00CD00"),
                // a tinted light bg
                Arguments.of("#F4F6F8", false, 3, "#757500"),
                Arguments.of("#F4F6F8", false, 8, "#4C4C4C"),
            )
    }
}
