package dev.quayside.projects.ui

import dev.quayside.sessions.api.SessionCounts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Label of a rail button: what a screen reader says about a project (NFR-6, plan AM-809). */
internal class RailLabelTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `rail label names the project and its counts`(
        @Suppress("UNUSED_PARAMETER") name: String,
        counts: SessionCounts,
        expected: String,
    ) {
        railLabel("Quayside", counts) shouldBe expected
    }

    @Test
    fun `rail label without counts is the name`() {
        railLabel("Quayside", null) shouldBe "Quayside"
        railLabel("Quayside", SessionCounts(working = 0, waiting = 0)) shouldBe "Quayside"
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("both", SessionCounts(working = 1, waiting = 2), "Quayside, 2 czekają, 1 pracuje"),
                arguments("one waiting", SessionCounts(working = 0, waiting = 1), "Quayside, 1 czeka"),
                arguments("working only", SessionCounts(working = 2, waiting = 0), "Quayside, 2 pracują"),
                arguments("five", SessionCounts(working = 5, waiting = 5), "Quayside, 5 czeka, 5 pracuje"),
                arguments(
                    "twelve is not few",
                    SessionCounts(working = 12, waiting = 12),
                    "Quayside, 12 czeka, 12 pracuje",
                ),
                arguments(
                    "twenty-two is few",
                    SessionCounts(working = 22, waiting = 24),
                    "Quayside, 24 czekają, 22 pracują",
                ),
            )
    }
}
