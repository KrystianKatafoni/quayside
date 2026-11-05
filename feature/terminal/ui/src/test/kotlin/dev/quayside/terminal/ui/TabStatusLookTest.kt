package dev.quayside.terminal.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Icon and tone of the status mark of a tab (plan AM-809, table 2 without PERMISSION). */
internal class TabStatusLookTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    fun `status maps to icon and tone`(
        status: TabStatus,
        icon: IconKind,
        tone: StatusTone,
    ) {
        status.look() shouldBe (icon to tone)
    }

    @Test
    fun `status icons are pairwise different`() {
        TabStatus.entries
            .map { it.look().first }
            .toSet()
            .size shouldBe TabStatus.entries.size
    }

    companion object {
        @JvmStatic
        fun table(): List<Arguments> =
            listOf(
                arguments(TabStatus.WAITING, IconKind.WAITING, StatusTone.WARN),
                arguments(TabStatus.WORKING, IconKind.WORKING, StatusTone.WORK),
                arguments(TabStatus.DONE, IconKind.DONE, StatusTone.OK),
                arguments(TabStatus.FAILED, IconKind.FAILED, StatusTone.ERR),
                arguments(TabStatus.STOPPED, IconKind.STOPPED, StatusTone.NEUTRAL),
            )
    }
}
