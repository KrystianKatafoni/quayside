package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import java.awt.Color

private const val TRACK_PERCENT = 6
private const val THUMB_BASE_PERCENT = 45
private const val PERCENT_MAX = 100
private const val OPAQUE = 0xFF000000L
private const val RGB_MASK = 0xFFFFFFL

/**
 * Colours of the terminal scroll bar, all opaque. [thumb] has at least 3:1 on [track] and on [background] (NFR-6)
 * when `termText` has at least 4.5:1 on `termBg`; it is `termText` itself when no mix reaches 3:1.
 */
internal data class ScrollbarColors(
    val background: Rgba,
    val track: Rgba,
    val thumb: Rgba,
)

/**
 * Scroll bar colours derived from the terminal tokens: background `termBg`, track `termBg` mixed with `termText`
 * 6 %, thumb `termBg` mixed with `termText` by the smallest p in 45..100 that gives 3:1 on the track and on `termBg`;
 * `termText` itself when no p does. Alpha of both tokens is dropped (as AM-807).
 */
internal fun scrollbarColors(colors: TerminalColors): ScrollbarColors {
    val background = colors.termBg.opaque()
    val text = colors.termText.opaque()
    val track = background.mix(text, TRACK_PERCENT)
    val thumb =
        (THUMB_BASE_PERCENT..PERCENT_MAX)
            .asSequence()
            .map { background.mix(text, it) }
            // The track lies between background and thumb, so the background check is a safeguard only.
            .firstOrNull { meetsNonText(it, track) && meetsNonText(it, background) }
            ?: text
    return ScrollbarColors(background, track, thumb)
}

private fun meetsNonText(
    thumb: Rgba,
    behind: Rgba,
): Boolean = Wcag.contrast(thumb, behind) >= Wcag.NON_TEXT_MIN

private fun Rgba.opaque(): Rgba = Rgba(OPAQUE or (argb and RGB_MASK))

/** Opaque AWT colour with the red, green and blue of this token; alpha is dropped. */
internal fun Rgba.toAwtColor(): Color = Color(red, green, blue)
