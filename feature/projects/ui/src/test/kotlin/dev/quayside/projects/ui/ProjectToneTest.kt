package dev.quayside.projects.ui

import dev.quayside.core.designsystem.StatusTone
import dev.quayside.sessions.api.SessionCounts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Tone of the folder icon of a project (plan AM-809, table 3). */
internal class ProjectToneTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `tone follows the counts`(
        @Suppress("UNUSED_PARAMETER") name: String,
        counts: SessionCounts?,
        expected: StatusTone,
    ) {
        projectTone(counts) shouldBe expected
    }

    @Test
    fun `waiting wins over working`() {
        projectTone(SessionCounts(working = 5, waiting = 1)) shouldBe StatusTone.WARN
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("waiting only", SessionCounts(working = 0, waiting = 2), StatusTone.WARN),
                arguments("working only", SessionCounts(working = 3, waiting = 0), StatusTone.WORK),
                arguments("zeros", SessionCounts(working = 0, waiting = 0), StatusTone.NEUTRAL),
                arguments("no counts", null, StatusTone.NEUTRAL),
            )
    }
}
