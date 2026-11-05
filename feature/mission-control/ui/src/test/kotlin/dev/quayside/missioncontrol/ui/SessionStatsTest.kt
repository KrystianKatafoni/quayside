package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The four counters of the stat cards of the project page (plan AM-809). */
internal class SessionStatsTest {
    @Test
    fun `stats count waiting working done failed`() {
        // Given a different number of sessions in each group, so a swapped counter is noticed
        val states =
            listOf(
                SessionState.AwaitingInput("q"),
                SessionState.AwaitingPermission("Bash"),
                SessionState.Working,
                SessionState.Working,
                SessionState.Working,
                SessionState.Done,
                SessionState.Failed,
                SessionState.Failed,
                SessionState.Failed,
                SessionState.Failed,
            )

        SessionStats.of(states) shouldBe SessionStats(waiting = 2, working = 3, done = 1, failed = 4)
    }

    @Test
    fun `stopped sessions are not counted`() {
        val states = listOf(SessionState.Stopped, SessionState.Stopped, SessionState.Working)

        SessionStats.of(states) shouldBe SessionStats(waiting = 0, working = 1, done = 0, failed = 0)
    }

    @Test
    fun `no sessions give zero everywhere`() {
        SessionStats.of(emptyList()) shouldBe SessionStats(waiting = 0, working = 0, done = 0, failed = 0)
    }
}
