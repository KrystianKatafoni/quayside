package dev.quayside.core.designtokens

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// All functions here are pure and the types immutable: safe to use from any thread.
// Values from users (theme.json, AM-804) must go through `Rgba.parse(): Rgba?` / Outcome, not through
// the throwing constructors and operators below.
private const val CHANNEL_MAX = 255
private const val PERCENT_MAX = 100
private const val HUE_FULL = 360.0
private const val HUE_SECTOR = 60.0
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val SECTORS = 6.0
private const val GREEN_OFFSET = 2.0
private const val BLUE_OFFSET = 4.0

private fun mixChannel(
    from: Int,
    to: Int,
    percent: Int,
): Int = Math.floorDiv(from * PERCENT_MAX + (to - from) * percent + PERCENT_MAX / 2, PERCENT_MAX)

private fun pack(
    alpha: Int,
    red: Int,
    green: Int,
    blue: Int,
): Rgba =
    Rgba(
        (alpha.toLong() shl ALPHA_SHIFT) or (red.toLong() shl RED_SHIFT) or (green.toLong() shl GREEN_SHIFT) or
            blue.toLong(),
    )

/**
 * Channel-wise sRGB mix: [percent] of [other] (0..100), rounded; alpha of the receiver.
 *
 * @throws IllegalArgumentException when [percent] is outside 0..100.
 */
public fun Rgba.mix(
    other: Rgba,
    percent: Int,
): Rgba {
    require(percent in 0..PERCENT_MAX) { "percent must be in 0..100" }
    return pack(
        alpha,
        mixChannel(red, other.red, percent),
        mixChannel(green, other.green, percent),
        mixChannel(blue, other.blue, percent),
    )
}

/**
 * Alpha compositing of the receiver over an opaque [background]; result is opaque.
 *
 * @throws IllegalArgumentException when [background] is not opaque.
 */
public fun Rgba.over(background: Rgba): Rgba {
    require(background.isOpaque) { "background must be opaque" }
    val percent = alpha * PERCENT_MAX.toDouble() / CHANNEL_MAX

    fun blend(
        front: Int,
        back: Int,
    ): Int = Math.round(back + (front - back) * percent / PERCENT_MAX).toInt()
    return pack(CHANNEL_MAX, blend(red, background.red), blend(green, background.green), blend(blue, background.blue))
}

/** HSL hue in [0, 360), or null for greys (red == green == blue). */
public fun Rgba.hue(): Double? {
    val high = max(red, max(green, blue))
    val delta = (high - min(red, min(green, blue))).toDouble()
    if (delta == 0.0) return null
    val sector =
        when (high) {
            red -> ((green - blue) / delta).mod(SECTORS)
            green -> (blue - red) / delta + GREEN_OFFSET
            else -> (red - green) / delta + BLUE_OFFSET
        }
    return (sector * HUE_SECTOR).mod(HUE_FULL)
}

/** Shortest distance between hues in [0, 180]; null when either colour is grey. */
public fun hueDistance(
    a: Rgba,
    b: Rgba,
): Double? {
    val first = a.hue()
    val second = b.hue()
    if (first == null || second == null) return null
    val diff = abs(first - second)
    return min(diff, HUE_FULL - diff)
}

/** WCAG 2.x relative luminance and contrast; a translucent foreground is composited over the opaque background. */
public object Wcag {
    /** Minimum contrast of text (NFR-6). */
    public const val TEXT_MIN: Double = 4.5

    /** Minimum contrast of non-text elements such as status colours. */
    public const val NON_TEXT_MIN: Double = 3.0

    private const val LINEAR_LIMIT = 0.04045
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05

    private fun linear(channel: Int): Double {
        val value = channel / CHANNEL_MAX.toDouble()
        return if (value <= LINEAR_LIMIT) {
            value / LINEAR_DIVISOR
        } else {
            ((value + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        }
    }

    /**
     * Relative luminance 0.0..1.0 of an opaque [color].
     *
     * @throws IllegalArgumentException when [color] is not opaque.
     */
    public fun luminance(color: Rgba): Double {
        require(color.isOpaque) { "color must be opaque" }
        return RED_WEIGHT * linear(color.red) + GREEN_WEIGHT * linear(color.green) + BLUE_WEIGHT * linear(color.blue)
    }

    /**
     * Contrast ratio 1.0..21.0. A translucent [foreground] is composited over [background] first.
     *
     * @throws IllegalArgumentException when [background] is not opaque (the foreground is then
     *   composited over it, or its luminance is taken, which requires an opaque colour).
     */
    public fun contrast(
        foreground: Rgba,
        background: Rgba,
    ): Double {
        val front = luminance(if (foreground.isOpaque) foreground else foreground.over(background))
        val back = luminance(background)
        return (max(front, back) + FLARE) / (min(front, back) + FLARE)
    }

    /** Black or white, whichever contrasts more with [background]. */
    public fun inkFor(background: Rgba): Rgba =
        if (contrast(Rgba.BLACK, background) >= contrast(Rgba.WHITE, background)) Rgba.BLACK else Rgba.WHITE
}
