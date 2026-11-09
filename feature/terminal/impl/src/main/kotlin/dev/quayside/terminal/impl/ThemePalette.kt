package dev.quayside.terminal.impl

import com.jediterm.core.Color
import com.jediterm.terminal.emulator.ColorPalette
import dev.quayside.core.designtokens.AnsiColors

/** JediTerm palette of the theme's 16 ANSI colours (AM-811, ADR 0027); text and background use the same colour. */
internal class ThemePalette(
    private val ansi: AnsiColors,
) : ColorPalette() {
    override fun getForegroundByColorIndex(colorIndex: Int): Color = ansi[colorIndex].toJediColor()

    override fun getBackgroundByColorIndex(colorIndex: Int): Color = ansi[colorIndex].toJediColor()
}
