package dev.quayside.missioncontrol.ui

import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Age of a session from its last activity (FR-S1): s / min / h / d, rounded down, never negative. Ages are in
 * seconds: JUnit cannot pass the inline class `Duration` as a parameter.
 */
internal class AgeTextTest {
    @ParameterizedTest(name = "{0} s -> {1}")
    @MethodSource("ages")
    fun `age is rounded down to the largest unit`(
        seconds: Long,
        expected: UiText,
    ) {
        AgeText.of(from = T0, now = T0.plusSeconds(seconds)) shouldBe expected
    }

    companion object {
        private const val MINUTE = 60L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        @JvmStatic
        fun ages(): List<Arguments> =
            listOf(
                arguments(0L, UiText("age.now")),
                arguments(59L, UiText("age.s", listOf("59"))),
                arguments(MINUTE, UiText("age.min", listOf("1"))),
                arguments(2 * MINUTE - 1, UiText("age.min", listOf("1"))),
                arguments(59 * MINUTE, UiText("age.min", listOf("59"))),
                arguments(HOUR - 1, UiText("age.min", listOf("59"))),
                arguments(HOUR, UiText("age.h", listOf("1"))),
                arguments(DAY - MINUTE, UiText("age.h", listOf("23"))),
                arguments(DAY, UiText("age.d", listOf("1"))),
                arguments(DAY + HOUR, UiText("age.d", listOf("1"))),
                arguments(3 * DAY, UiText("age.d", listOf("3"))),
                // The clock moved back: a session from the future is "now", not a negative age.
                arguments(-5L, UiText("age.now")),
                arguments(-2 * HOUR, UiText("age.now")),
            )
    }
}
