package dev.agentmanager.commandpalette.api

/** Modifier of a [KeyChord]; independent of Compose (ENG-I8). */
public enum class KeyModifier { META, SHIFT, ALT, CTRL }

/** The key of a [KeyChord]: a lower-case letter or digit, or a named key. */
public sealed interface ChordKey {
    /**
     * A letter or digit key.
     *
     * @throws IllegalArgumentException unless [char] is in `a..z` or `0..9`.
     */
    public data class Letter(
        val char: Char,
    ) : ChordKey {
        init {
            require(char in 'a'..'z' || char in '0'..'9') { "Letter key must be a-z or 0-9" }
        }
    }

    /** Keys without a printable character. */
    public enum class Named : ChordKey { ENTER, ESCAPE, SPACE, BACKSPACE, UP, DOWN }
}

/** One key press with its [modifiers], e.g. `⌘K`. */
public data class KeyChord(
    val key: ChordKey,
    val modifiers: Set<KeyModifier> = emptySet(),
)

/**
 * One chord (`⌘K`) or a sequence of two (`g p`).
 *
 * @throws IllegalArgumentException for 0 or more than 2 chords.
 */
public data class Shortcut(
    val chords: List<KeyChord>,
) {
    init {
        require(chords.size in 1..2) { "Shortcut needs one or two chords" }
    }

    public companion object {
        /** Builds a shortcut from [chords]. */
        public fun of(vararg chords: KeyChord): Shortcut = Shortcut(chords.toList())
    }
}
