package dev.quayside.core.designtokens

/**
 * sRGB colour with alpha, packed as 0xAARRGGBB. Immutable, safe to use from any thread.
 *
 * Input from users (theme.json, AM-804) must go through [parse] (returns null on bad input), not
 * through this constructor.
 *
 * @throws IllegalArgumentException when [argb] is outside 0..0xFFFFFFFF.
 */
@JvmInline
public value class Rgba(
    public val argb: Long,
) {
    init {
        require(argb in 0..MAX_ARGB) { "argb out of range" }
    }

    /** Alpha channel, 0..255. */
    public val alpha: Int get() = channel(ALPHA_SHIFT)

    /** Red channel, 0..255. */
    public val red: Int get() = channel(RED_SHIFT)

    /** Green channel, 0..255. */
    public val green: Int get() = channel(GREEN_SHIFT)

    /** Blue channel, 0..255. */
    public val blue: Int get() = channel(0)

    /** True when alpha is 255. */
    public val isOpaque: Boolean get() = alpha == CHANNEL_MAX

    /**
     * Same colour with alpha set to [percent] (0..100) of 255.
     *
     * @throws IllegalArgumentException when [percent] is outside 0..100.
     */
    public fun withAlpha(percent: Int): Rgba {
        require(percent in 0..PERCENT_MAX) { "percent must be in 0..100" }
        val newAlpha = Math.floorDiv(CHANNEL_MAX * percent * 2 + PERCENT_MAX, PERCENT_MAX * 2)
        return Rgba((argb and RGB_MASK) or (newAlpha.toLong() shl ALPHA_SHIFT))
    }

    private fun channel(shift: Int): Int = ((argb shr shift) and CHANNEL_MAX.toLong()).toInt()

    public companion object {
        /** Opaque black. */
        public val BLACK: Rgba = Rgba(OPAQUE_BLACK)

        /** Opaque white. */
        public val WHITE: Rgba = Rgba(OPAQUE_WHITE)

        /** `#RRGGBB` (any case) gives an opaque colour; anything else gives null, never throws. */
        public fun parse(hex: String): Rgba? {
            if (!HEX.matches(hex)) return null
            return Rgba(OPAQUE_BLACK or hex.substring(1).toLong(HEX_RADIX))
        }

        private val HEX = Regex("#[0-9A-Fa-f]{6}")
        private const val HEX_RADIX = 16
        private const val MAX_ARGB = 0xFFFF_FFFFL
        private const val RGB_MASK = 0x00FF_FFFFL
        private const val OPAQUE_BLACK = 0xFF00_0000L
        private const val OPAQUE_WHITE = 0xFFFF_FFFFL
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val CHANNEL_MAX = 255
        private const val PERCENT_MAX = 100
    }
}
