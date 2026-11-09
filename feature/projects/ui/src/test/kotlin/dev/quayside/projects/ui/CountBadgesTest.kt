package dev.quayside.projects.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The count badges of a sidebar row (FR-P6, AM-310): always four, in the order of the roadmap (waiting needs action,
 * then working, done, failed); zeros are kept, each with its own description.
 */
internal class CountBadgesTest {
    @Test
    fun `four badges in the order waiting working done failed`() {
        // Four distinct numbers, so that a swapped pair of badges cannot pass
        CountBadges.of(StatusCounts(waiting = 1, working = 2, done = 3, failed = 4)) shouldBe
            listOf(
                UiText("row.waiting", listOf("1")),
                UiText("row.working", listOf("2")),
                UiText("row.done", listOf("3")),
                UiText("row.failed", listOf("4")),
            )
    }

    @Test
    fun `zeros are kept with their description`() {
        CountBadges.of(StatusCounts()) shouldBe
            listOf(
                UiText("row.waiting", listOf("0")),
                UiText("row.working", listOf("0")),
                UiText("row.done", listOf("0")),
                UiText("row.failed", listOf("0")),
            )
        ProjectsStrings.format(UiText("row.waiting", listOf("0"))) shouldBe "czeka: 0"
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptions")
    fun `every badge reads its state and number`(
        @Suppress("UNUSED_PARAMETER") name: String,
        counts: StatusCounts,
        expected: List<String>,
    ) {
        CountBadges.of(counts).map(ProjectsStrings::format) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun descriptions(): List<Arguments> =
            listOf(
                arguments("all zero", StatusCounts(), listOf("czeka: 0", "pracuje: 0", "gotowe: 0", "błąd: 0")),
                arguments(
                    "only done",
                    StatusCounts(done = 7),
                    listOf("czeka: 0", "pracuje: 0", "gotowe: 7", "błąd: 0"),
                ),
                arguments(
                    "only failed",
                    StatusCounts(failed = 12),
                    listOf("czeka: 0", "pracuje: 0", "gotowe: 0", "błąd: 12"),
                ),
                arguments(
                    "all four",
                    StatusCounts(waiting = 2, working = 3, done = 4, failed = 5),
                    listOf("czeka: 2", "pracuje: 3", "gotowe: 4", "błąd: 5"),
                ),
            )
    }
}
