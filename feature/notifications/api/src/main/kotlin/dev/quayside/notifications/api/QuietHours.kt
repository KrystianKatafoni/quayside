package dev.quayside.notifications.api

import java.time.LocalTime

/**
 * Do-not-disturb hours and macOS Focus (FR-N6, docs/07). While quiet, voice and sound are dropped; popups and the
 * Dock badge stay. Immutable; times are whole minutes.
 *
 * @property enabled the hours apply; off by default. [respectFocus] works on its own.
 * @property from start of the quiet time, inclusive.
 * @property to end of the quiet time, exclusive; [to] before [from] crosses midnight; [from] equal to [to] is
 *   never quiet.
 * @property respectFocus quiet while a macOS Focus is on (no effect until the Focus adapter exists).
 */
public data class QuietHours(
    val enabled: Boolean = false,
    val from: LocalTime = DEFAULT_FROM,
    val to: LocalTime = DEFAULT_TO,
    val respectFocus: Boolean = true,
) {
    init {
        require(from.second == 0 && from.nano == 0) { "from must be a whole minute" }
        require(to.second == 0 && to.nano == 0) { "to must be a whole minute" }
    }

    public companion object {
        /** Default start of the quiet time (docs/07). */
        public val DEFAULT_FROM: LocalTime = LocalTime.of(22, 0)

        /** Default end of the quiet time (docs/07). */
        public val DEFAULT_TO: LocalTime = LocalTime.of(7, 0)
    }
}
