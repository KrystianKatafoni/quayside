package dev.quayside.appearance.api

import dev.quayside.core.designtokens.Rgba

/** The eight opaque colours of a custom scheme, as the columns of the docs/08 table. Immutable. */
public data class SchemeColors(
    val bg: Rgba,
    val panel: Rgba,
    val text: Rgba,
    val accent: Rgba,
    val warn: Rgba,
    val ok: Rgba,
    val err: Rgba,
    val work: Rgba,
) {
    init {
        require(all().all { it.isOpaque }) { "scheme colours must be opaque" }
    }

    /** The colours in column order (bg, panel, text, accent, warn, ok, err, work). */
    public fun all(): List<Rgba> = listOf(bg, panel, text, accent, warn, ok, err, work)
}

/**
 * A scheme made by the user (FR-A6): the eight colours of the docs/08 table, a corner [radius] and a [mode]; `impl`
 * derives the other tokens and checks NFR-6 before the scheme joins the catalog. Immutable.
 *
 * Values read from a file must be validated by a parser returning `T?` before this constructor is called.
 *
 * @throws IllegalArgumentException when [name] is blank or longer than [MAX_NAME] characters or [radius] is outside
 *   0..[MAX_RADIUS].
 */
public data class CustomScheme(
    val id: SchemeId,
    val name: String,
    val mode: ThemeMode,
    val colors: SchemeColors,
    val radius: Int,
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME) { "name must have 1..$MAX_NAME characters" }
        require(radius in 0..MAX_RADIUS) { "radius must be in 0..$MAX_RADIUS" }
    }

    public companion object {
        /** Longest name, in characters. */
        public const val MAX_NAME: Int = 40

        /** Largest corner radius, in dp. */
        public const val MAX_RADIUS: Int = 24
    }
}
