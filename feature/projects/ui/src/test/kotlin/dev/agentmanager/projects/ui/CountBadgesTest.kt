package dev.agentmanager.projects.ui

import dev.agentmanager.sessions.api.SessionCounts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The count badges of a sidebar row (FR-P6): zeros give no text, waiting comes first because it needs action. */
internal class CountBadgesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `badges show only non-zero counts and waiting first`(
        @Suppress("UNUSED_PARAMETER") name: String,
        counts: SessionCounts?,
        expected: List<UiText>,
    ) {
        CountBadges.of(counts) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("no counts", null, emptyList<UiText>()),
                arguments("zeros", SessionCounts(working = 0, waiting = 0), emptyList<UiText>()),
                arguments(
                    "working only",
                    SessionCounts(working = 2, waiting = 0),
                    listOf(UiText("row.working", listOf("2"))),
                ),
                arguments(
                    "waiting only",
                    SessionCounts(working = 0, waiting = 1),
                    listOf(UiText("row.waiting", listOf("1"))),
                ),
                arguments(
                    "both",
                    SessionCounts(working = 3, waiting = 1),
                    listOf(UiText("row.waiting", listOf("1")), UiText("row.working", listOf("3"))),
                ),
            )
    }
}
