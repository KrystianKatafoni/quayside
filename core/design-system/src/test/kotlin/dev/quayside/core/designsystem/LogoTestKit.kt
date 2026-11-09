package dev.quayside.core.designsystem

import androidx.compose.ui.graphics.PixelMap
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.over
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeInRange

/** Fixed colours of the mark (ADR 0030), spelled out here on purpose: a changed drawing must fail the tests. */
internal object LogoColors {
    val tile = rgb(0x1E1930)
    val beam = rgb(0xFBBF24)
    val lantern = rgb(0xE8E5F2)
    val violet = rgb(0xA78BFA)

    /** [color] at [percent] opacity over the tile, as the SVG `opacity` draws it. */
    fun onTile(
        color: Rgba,
        percent: Int,
    ): Rgba = color.withAlpha(percent).over(tile)
}

/** A point of the table in docs/plans/AM-822.md (viewBox units) and the colour expected there. */
internal class LogoPoint(
    val label: String,
    val x: Double,
    val y: Double,
    val expected: Rgba,
)

/** Table 2 of the plan, drawing SMALL (viewBox 16). */
internal fun smallPoints(): List<LogoPoint> =
    listOf(
        LogoPoint("tile", 1.5, 8.0, LogoColors.tile),
        LogoPoint("main beam", 13.5, 4.5, LogoColors.beam),
        LogoPoint("lantern", 8.0, 10.0, LogoColors.lantern),
        LogoPoint("head", 8.0, 4.8, LogoColors.violet),
        LogoPoint("no second beam", 3.0, 4.9, LogoColors.tile),
        LogoPoint("base", 8.0, 13.5, LogoColors.violet),
    )

/** Table 2 of the plan, drawing FULL (viewBox 32). */
internal fun fullPoints(): List<LogoPoint> =
    listOf(
        LogoPoint("tile", 3.0, 20.0, LogoColors.tile),
        LogoPoint("main beam", 26.0, 8.0, LogoColors.onTile(LogoColors.beam, 90)),
        LogoPoint("lantern", 16.0, 22.0, LogoColors.lantern),
        LogoPoint("window", 16.0, 17.9, LogoColors.violet),
        LogoPoint("second beam", 6.0, 9.8, LogoColors.onTile(LogoColors.beam, 45)),
        LogoPoint("base", 16.0, 26.4, LogoColors.violet),
    )

/** Pixel index of a viewBox coordinate when [viewBox] units are drawn on [sizePx] pixels. */
internal fun unitToPixel(
    unit: Double,
    sizePx: Int,
    viewBox: Int,
): Int = (unit * sizePx / viewBox).toInt()

/** Fails unless every channel of [actual] is within [tolerance] of [expected] (software rendering, anti-aliasing). */
internal fun shouldBeNear(
    actual: Int,
    expected: Int,
    tolerance: Int = LOGO_TOLERANCE,
    clue: String = "",
) {
    withClue("$clue actual=${actual.toUInt().toString(HEX)} expected=${expected.toUInt().toString(HEX)}") {
        for (shift in CHANNEL_SHIFTS) {
            val a = (actual shr shift) and CHANNEL_MASK
            val e = (expected shr shift) and CHANNEL_MASK
            a shouldBeInRange (e - tolerance)..(e + tolerance)
        }
    }
}

/** Every distinct ARGB in the columns [from] until [until] of the pixels. */
internal fun PixelMap.distinctColorsIn(
    from: Int,
    until: Int,
): Set<Int> {
    val found = HashSet<Int>()
    for (y in 0 until height) for (x in from.coerceAtLeast(0) until until.coerceAtMost(width)) found.add(argbAt(x, y))
    return found
}

internal const val LOGO_TOLERANCE = 2
private const val CHANNEL_MASK = 0xFF
private const val HEX = 16
private val CHANNEL_SHIFTS = intArrayOf(0, 8, 16, 24)
