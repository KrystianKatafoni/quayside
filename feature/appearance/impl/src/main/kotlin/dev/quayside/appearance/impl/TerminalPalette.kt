package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors

private const val LAST_INDEX = AnsiColors.SIZE - 1

/** The xterm palette of `ColorPaletteImpl.XTERM_PALETTE` in JediTerm 3.76, before the contrast correction. */
internal val XTERM_BASE: AnsiColors =
    AnsiColors(
        listOf(
            0x000000L,
            0xCD0000L,
            0x00CD00L,
            0xCDCD00L,
            0x1E90FFL,
            0xCD00CDL,
            0x00CDCDL,
            0xE5E5E5L,
            0x4C4C4CL,
            0xFF0000L,
            0x00FF00L,
            0xFFFF00L,
            0x4682B4L,
            0xFF00FFL,
            0x00FFFFL,
            0xFFFFFFL,
        ).map { Rgba(0xFF000000L or it) },
    )

/**
 * Terminal colours of a theme (AM-811, ADR 0027): [bg] and [text] as they are, the xterm palette corrected by
 * [readableOn] to 4.5:1 on [bg] toward white ([dark]) or black, except the background colour of the mode
 * (index 0 when [dark], 15 otherwise), which stays as given.
 *
 * @throws IllegalArgumentException when [bg] is not opaque (from `Wcag.contrast`); catalog rows only, a theme file
 * would get an `Outcome` (AM-814).
 */
internal fun deriveTerminal(
    bg: Rgba,
    text: Rgba,
    dark: Boolean,
): TerminalColors {
    val keep = if (dark) 0 else LAST_INDEX
    val ansi =
        XTERM_BASE.colors.mapIndexed { index, color ->
            if (index == keep) color else color.readableOn(listOf(bg), dark)
        }
    return TerminalColors(bg, text, AnsiColors(ansi))
}
