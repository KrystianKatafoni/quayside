package dev.quayside.core.designtokens

/**
 * Shape tokens in dp (FR-A7). Immutable, safe to use from any thread.
 *
 * @throws IllegalArgumentException when [radius] or [borderWidth] is negative.
 */
public data class ShapeTokens(
    val radius: Int,
    val borderWidth: Int,
    val shadow: ShadowSpec?,
    val backdrop: Backdrop,
) {
    init {
        require(radius >= 0) { "radius must be >= 0" }
        require(borderWidth >= 0) { "borderWidth must be >= 0" }
    }
}

/**
 * Panel shadow; horizontal offset is always 0. Immutable, safe to use from any thread.
 *
 * @throws IllegalArgumentException when [blur] is negative.
 */
public data class ShadowSpec(
    val offsetY: Int,
    val blur: Int,
    val spread: Int,
    val color: Rgba,
) {
    init {
        require(blur >= 0) { "blur must be >= 0" }
    }
}

/** OPAQUE panel, or TRANSLUCENT: panel colour with alpha over bg, no blur. */
public enum class Backdrop { OPAQUE, TRANSLUCENT }
