package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Filter Wszystkie / Aktywne / Zakończone (FR-S9, first half): 3 filters × 6 states. */
internal class SessionFilterTest {
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("cases")
    fun `filter matches by the active rule of sessions api`(
        filter: SessionFilter,
        state: SessionState,
        expected: Boolean,
    ) {
        filter.matches(state) shouldBe expected
    }

    companion object {
        private val active =
            listOf(SessionState.Working, SessionState.AwaitingInput("q"), SessionState.AwaitingPermission("Bash"))
        private val finished = listOf(SessionState.Done, SessionState.Failed, SessionState.Stopped)

        @JvmStatic
        fun cases(): List<Arguments> =
            (active + finished).flatMap { state ->
                listOf(
                    arguments(SessionFilter.ALL, state, true),
                    arguments(SessionFilter.ACTIVE, state, state in active),
                    arguments(SessionFilter.FINISHED, state, state in finished),
                )
            }
    }
}
