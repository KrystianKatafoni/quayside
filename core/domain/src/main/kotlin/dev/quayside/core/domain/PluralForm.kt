package dev.quayside.core.domain

/**
 * Grammatical number of a Polish noun or verb after a count, e.g. "1 czeka", "2 czekają", "5 czeka".
 * UI modules pick the text by [key] (e.g. `overview.waiting.few`) and fill in the count themselves.
 */
public enum class PluralForm(
    /** Suffix of the text key in the i18n properties files: `one`, `few` or `many`. */
    public val key: String,
) {
    /** Exactly 1. */
    ONE("one"),

    /** Last digit 2-4, except 12-14 (2, 3, 4, 22, 24, 102). */
    FEW("few"),

    /** Everything else: 0, 5-21, 25-31, 112-114 and every negative count. */
    MANY("many"),
    ;

    public companion object {
        private const val LAST_DIGIT_MOD = 10
        private const val LAST_TWO_DIGITS_MOD = 100
        private const val MAX_FEW_DIGIT = 4
        private val FEW_DIGITS = 2..MAX_FEW_DIGIT
        private val TEEN_RANGE = 12..14

        /** Polish form for [count]; pure, never throws. Negative counts (not produced by counters) are [MANY]. */
        public fun of(count: Int): PluralForm =
            when {
                count == 1 -> ONE
                count % LAST_DIGIT_MOD in FEW_DIGITS && count % LAST_TWO_DIGITS_MOD !in TEEN_RANGE -> FEW
                else -> MANY
            }
    }
}
