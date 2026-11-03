package dev.quayside.core.designtokens

/**
 * Key of a typography as stored in theme.json, e.g. `plex`. Immutable, safe to use from any thread.
 *
 * Keys read from user files (AM-804) must be validated by the parser (`parse(): T?` / Outcome)
 * before this constructor is called.
 *
 * @throws IllegalArgumentException when [value] does not match `[a-z0-9-]+`.
 */
@JvmInline
public value class TypographyId(
    public val value: String,
) {
    init {
        require(KEY.matches(value)) { "typography key must match [a-z0-9-]+" }
    }

    private companion object {
        private val KEY = Regex("[a-z0-9-]+")
    }
}

/** Pair of font families: [uiFont] for the interface, [terminalFont] for terminals. */
public data class TypographySpec(
    val id: TypographyId,
    val name: String,
    val uiFont: String,
    val terminalFont: String,
)
