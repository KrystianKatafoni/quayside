package dev.quayside.projects.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The chips of a project row (FR-P6, AM-310 as reduced): waiting (needs action), working, failed, in that order; a
 * state with no sessions has no chip, each chip has its own text, tone and icon.
 */
internal class CountBadgesTest {
    @Test
    fun `three chips in the order waiting working failed with their tone and icon`() {
        // Three distinct numbers, so that a swapped pair of chips cannot pass
        CountBadges.of(StatusCounts(waiting = 1, working = 2, failed = 4)) shouldBe
            listOf(
                CountBadge(UiText("row.waiting", listOf("1")), StatusTone.WARN, IconKind.WAITING),
                CountBadge(UiText("row.working", listOf("2")), StatusTone.WORK, IconKind.WORKING),
                CountBadge(UiText("row.failed", listOf("4")), StatusTone.ERR, IconKind.FAILED),
            )
    }

    @Test
    fun `no sessions give no chip`() {
        CountBadges.of(StatusCounts()) shouldBe emptyList()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptions")
    fun `only non zero states get a chip and it reads its state and number`(
        @Suppress("UNUSED_PARAMETER") name: String,
        counts: StatusCounts,
        expected: List<String>,
    ) {
        CountBadges.of(counts).map { ProjectsStrings.format(it.text) } shouldBe expected
    }

    companion object {
        @JvmStatic
        fun descriptions(): List<Arguments> =
            listOf(
                arguments("all zero", StatusCounts(), emptyList<String>()),
                arguments("only waiting", StatusCounts(waiting = 3), listOf("czeka: 3")),
                arguments("only working", StatusCounts(working = 9), listOf("pracuje: 9")),
                arguments("only failed", StatusCounts(failed = 12), listOf("błąd: 12")),
                arguments("waiting and failed", StatusCounts(waiting = 1, failed = 2), listOf("czeka: 1", "błąd: 2")),
                arguments("working and failed", StatusCounts(working = 4, failed = 5), listOf("pracuje: 4", "błąd: 5")),
                arguments(
                    "all three",
                    StatusCounts(waiting = 2, working = 3, failed = 5),
                    listOf("czeka: 2", "pracuje: 3", "błąd: 5"),
                ),
            )
    }
}
