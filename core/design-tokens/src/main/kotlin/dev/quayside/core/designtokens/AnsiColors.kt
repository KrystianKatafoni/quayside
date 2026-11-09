package dev.quayside.core.designtokens

/**
 * The 16 ANSI colours of a terminal in xterm order: 0-7 black, red, green, yellow, blue, magenta, cyan, white;
 * 8-15 their bright variants. Immutable.
 *
 * @throws IllegalArgumentException when [colors] does not hold exactly [SIZE] colours (programmer error: only
 * catalog constants build it; data from users will go through a `parse(): AnsiColors?`, AM-814).
 */
public class AnsiColors(
    colors: List<Rgba>,
) {
    /** A copy of the given colours; later changes of the source list do not reach it. */
    public val colors: List<Rgba> = colors.toList()

    init {
        require(this.colors.size == SIZE) { "an ANSI palette needs $SIZE colours, got ${this.colors.size}" }
    }

    /** The colour of xterm index [index] (0-15). @throws IndexOutOfBoundsException outside 0-15. */
    public operator fun get(index: Int): Rgba = colors[index]

    /** Equal when all 16 colours are equal. */
    override fun equals(other: Any?): Boolean = other is AnsiColors && other.colors == colors

    /** Follows [colors]. */
    override fun hashCode(): Int = colors.hashCode()

    /** Follows [colors]. */
    override fun toString(): String = "AnsiColors($colors)"

    public companion object {
        /** Number of colours. */
        public const val SIZE: Int = 16
    }
}
