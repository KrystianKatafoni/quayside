package dev.quayside.projects.ui

import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The counters of a sidebar row (FR-P6, AM-310 as reduced): one field per state; done and stopped sessions nowhere. */
internal class StatusCountsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("oneState")
    fun `a session lands in exactly its field`(
        @Suppress("UNUSED_PARAMETER") name: String,
        state: SessionState,
        expected: StatusCounts,
    ) {
        StatusCounts.of(listOf(state)) shouldBe expected
    }

    @Test
    fun `counts accumulate per field and done and stopped are ignored`() {
        // Distinct numbers (5, 2, 4) so that a swapped pair of fields cannot pass
        val states =
            listOf(
                SessionState.Failed,
                SessionState.Done,
                SessionState.Working,
                SessionState.AwaitingInput("q"),
                SessionState.Stopped,
                SessionState.Failed,
                SessionState.Done,
                SessionState.Working,
                SessionState.AwaitingPermission("Bash"),
                SessionState.Failed,
                SessionState.Done,
                SessionState.AwaitingPermission(null),
                SessionState.Failed,
                SessionState.Stopped,
                SessionState.AwaitingInput(null),
                SessionState.AwaitingInput("again"),
                SessionState.Stopped,
                SessionState.Done,
                SessionState.Done,
                SessionState.Done,
            )

        StatusCounts.of(states) shouldBe StatusCounts(waiting = 5, working = 2, failed = 4)
    }

    @Test
    fun `no sessions give three zeros`() {
        StatusCounts.of(emptyList()) shouldBe StatusCounts(waiting = 0, working = 0, failed = 0)
        StatusCounts() shouldBe StatusCounts(0, 0, 0)
    }

    @Test
    fun `active keeps only waiting and working for the folder tone`() {
        val counts = StatusCounts(waiting = 2, working = 3, failed = 5)

        counts.active shouldBe SessionCounts(working = 3, waiting = 2)
        StatusCounts(failed = 1).active shouldBe SessionCounts(working = 0, waiting = 0)
    }

    companion object {
        private val waits = StatusCounts(waiting = 1)

        @JvmStatic
        fun oneState(): List<Arguments> =
            listOf(
                arguments("working", SessionState.Working, StatusCounts(working = 1)),
                arguments("awaiting input", SessionState.AwaitingInput("q"), StatusCounts(waiting = 1)),
                arguments("awaiting input, no question", SessionState.AwaitingInput(null), StatusCounts(waiting = 1)),
                arguments("awaiting permission", SessionState.AwaitingPermission("Bash"), StatusCounts(waiting = 1)),
                arguments("awaiting permission, no tool", SessionState.AwaitingPermission(null), waits),
                arguments("done", SessionState.Done, StatusCounts()),
                arguments("failed", SessionState.Failed, StatusCounts(failed = 1)),
                arguments("stopped", SessionState.Stopped, StatusCounts()),
            )
    }
}
