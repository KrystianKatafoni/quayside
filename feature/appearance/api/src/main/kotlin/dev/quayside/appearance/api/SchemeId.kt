package dev.quayside.appearance.api

/**
 * Key of a theme as stored in theme.json (`scheme`, `dayScheme`, `nightScheme`). Immutable, safe to
 * use from any thread.
 *
 * Keys read from user files (AM-804) must be validated by the parser (`parse(): T?` / Outcome)
 * before this constructor is called.
 *
 * @throws IllegalArgumentException when [value] does not match `[a-z0-9-]+`.
 */
@JvmInline
public value class SchemeId(
    public val value: String,
) {
    init {
        require(KEY.matches(value)) { "scheme key must match [a-z0-9-]+" }
    }

    private companion object {
        private val KEY = Regex("[a-z0-9-]+")
    }
}
