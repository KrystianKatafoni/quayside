package dev.quayside.terminal.impl

import com.jediterm.terminal.emulator.ColorPalette
import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.designtokens.Rgba
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * The JediTerm palette of a theme (AM-811, ADR 0027): index N of the palette is `ansi[N]` for text and background.
 * The 16 colours are `#0N1N2N`, so a swapped index or channel changes the result. JediTerm
 * 3.76: `ColorPalette.getForeground/getBackground(TerminalColor)` ask `...ByColorIndex(int)` for indices below 16.
 */
class ThemePaletteTest {
    private val ansi = distinctAnsi()
    private val palette = ThemePalette(ansi)

    private fun ColorPalette.fg(index: Int): Int = foregroundRgb(index)

    private fun ColorPalette.bg(index: Int): Int = backgroundRgb(index)

    private fun expected(index: Int): Int = ansi[index].argb.toInt()

    @ParameterizedTest(name = "foreground of index {0} is ansi {0}")
    @MethodSource("indices")
    fun `foreground of index N is ansi N`(index: Int) {
        palette.fg(index) shouldBe expected(index)
    }

    @ParameterizedTest(name = "background of index {0} is ansi {0}")
    @MethodSource("indices")
    fun `background of index N is ansi N`(index: Int) {
        palette.bg(index) shouldBe expected(index)
    }

    @Test
    fun `colours of the palette are distinct so the table can see a swap`() {
        (0 until AnsiColors.SIZE).map { expected(it) }.toSet().size shouldBe AnsiColors.SIZE
    }

    @Test
    fun `drops alpha`() {
        val source = distinctAnsi().colors
        val translucent =
            AnsiColors(source.mapIndexed { i, c -> if (i == 3) Rgba((0x40L shl 24) or (c.argb and RGB)) else c })
        val result = ThemePalette(translucent)

        result.fg(3) shouldBe expected(3)
        result.bg(3) shouldBe expected(3)
    }

    companion object {
        private const val RGB = 0xFFFFFFL

        @JvmStatic
        fun indices(): Stream<Arguments> = (0 until AnsiColors.SIZE).map { Arguments.of(it) }.stream()
    }
}
