package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.testing.T0
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.SnapshotStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Freshness of the snapshot (docs/11 §7) for the mode bar and the stale banner. A stale text has one key per
 * [RuntimeProblem] (`fresh.stale.<PROBLEM>`, the time as the only argument), so each problem gets a fixed text.
 * The short text of the mode bar has only the time.
 */
internal class FreshnessTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("statuses")
    fun `freshness text follows the snapshot status`(
        status: SnapshotStatus,
        expected: UiText,
    ) {
        Freshness.of(status, ZoneOffset.UTC) shouldBe expected
    }

    @Test
    fun `stale time is formatted in the given zone, not the system zone`() {
        val stale = SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)

        Freshness.of(stale, ZoneId.of("Europe/Warsaw")) shouldBe UiText("fresh.stale.TIMEOUT", listOf("10:00:05"))
    }

    @Test
    fun `short text of the mode bar has the time but not the problem`() {
        val stale = SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)
        val never = SnapshotStatus.Stale(null, RuntimeProblem.TIMEOUT)

        Freshness.of(stale, ZoneOffset.UTC, short = true) shouldBe UiText("fresh.short.stale", listOf("09:00:05"))
        Freshness.of(never, ZoneOffset.UTC, short = true) shouldBe UiText("fresh.short.never")
        Freshness.of(SnapshotStatus.Fresh(T0), ZoneOffset.UTC, short = true) shouldBe UiText("fresh.live")
        Freshness.of(SnapshotStatus.Loading, ZoneOffset.UTC, short = true) shouldBe UiText("fresh.loading")
    }

    companion object {
        /** A listing at 09:00:00 UTC that went stale with [problem]: its text is [key] with the time. */
        private fun staleAtT0(
            problem: RuntimeProblem,
            key: String,
        ): Arguments = arguments(SnapshotStatus.Stale(T0, problem), UiText(key, listOf("09:00:00")))

        @JvmStatic
        fun statuses(): List<Arguments> =
            listOf(
                arguments(SnapshotStatus.Loading, UiText("fresh.loading")),
                arguments(SnapshotStatus.Fresh(T0), UiText("fresh.live")),
                arguments(
                    SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT),
                    UiText("fresh.stale.TIMEOUT", listOf("09:00:05")),
                ),
                staleAtT0(RuntimeProblem.NOT_INSTALLED, "fresh.stale.NOT_INSTALLED"),
                staleAtT0(RuntimeProblem.UNSUPPORTED_VERSION, "fresh.stale.UNSUPPORTED_VERSION"),
                staleAtT0(RuntimeProblem.AGENT_VIEW_DISABLED, "fresh.stale.AGENT_VIEW_DISABLED"),
                staleAtT0(RuntimeProblem.COMMAND_FAILED, "fresh.stale.COMMAND_FAILED"),
                staleAtT0(RuntimeProblem.UNPARSEABLE, "fresh.stale.UNPARSEABLE"),
                arguments(
                    SnapshotStatus.Stale(null, RuntimeProblem.NOT_INSTALLED),
                    UiText("fresh.never.NOT_INSTALLED"),
                ),
                arguments(SnapshotStatus.Stale(null, RuntimeProblem.TIMEOUT), UiText("fresh.never.TIMEOUT")),
            )
    }
}
