package dev.quayside.terminal.impl

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.emulator.ColorPalette
import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.designtokens.Rgba

/** Index N is `#0N1N2N`: 16 colours with different channels, so a swapped index or channel changes the result. */
internal fun distinctAnsi(): AnsiColors =
    AnsiColors(
        (0 until AnsiColors.SIZE).map { n ->
            Rgba(0xFF000000L or (n.toLong() shl 16) or ((0x10 + n).toLong() shl 8) or (0x20 + n).toLong())
        },
    )

/** The palette other tests of this module give to colours they do not care about. */
internal val TEST_ANSI: AnsiColors = distinctAnsi()

private fun ColorPalette.indexed(index: Int): TerminalColor =
    requireNotNull(ColorPalette.getIndexedTerminalColor(index)) { "no indexed colour $index" }

/** ARGB of the foreground colour of palette index [index]. */
internal fun ColorPalette.foregroundRgb(index: Int): Int = getForeground(indexed(index)).rgb

/** ARGB of the background colour of palette index [index]. */
internal fun ColorPalette.backgroundRgb(index: Int): Int = getBackground(indexed(index)).rgb
