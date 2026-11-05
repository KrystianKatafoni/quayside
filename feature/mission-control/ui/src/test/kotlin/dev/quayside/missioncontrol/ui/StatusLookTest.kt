package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Icon, tone and state text of every session state (FR-S2, plan AM-809 table 2). */
internal class StatusLookTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("looks")
    fun `state maps to icon tone and text key`(
        state: SessionState,
        expected: Glyph,
    ) {
        StatusLook.of(state) shouldBe expected
    }

    @Test
    fun `status icons are pairwise different`() {
        val states =
            listOf(
                SessionState.AwaitingInput("q"),
                SessionState.AwaitingPermission("Bash"),
                SessionState.Working,
                SessionState.Done,
                SessionState.Failed,
                SessionState.Stopped,
            )

        val icons = states.map { StatusLook.of(it).icon }

        icons.toSet().size shouldBe states.size
    }

    companion object {
        @JvmStatic
        fun looks(): List<Arguments> =
            listOf(
                arguments(
                    SessionState.AwaitingInput("Which branch?"),
                    look(IconKind.WAITING, StatusTone.WARN, "awaitingInput"),
                ),
                arguments(SessionState.AwaitingInput(null), look(IconKind.WAITING, StatusTone.WARN, "awaitingInput")),
                arguments(
                    SessionState.AwaitingPermission("Bash"),
                    look(IconKind.PERMISSION, StatusTone.WARN, "awaitingPermission"),
                ),
                arguments(
                    SessionState.AwaitingPermission(null),
                    look(IconKind.PERMISSION, StatusTone.WARN, "awaitingPermission"),
                ),
                arguments(SessionState.Working, look(IconKind.WORKING, StatusTone.WORK, "working")),
                arguments(SessionState.Done, look(IconKind.DONE, StatusTone.OK, "done")),
                arguments(SessionState.Failed, look(IconKind.FAILED, StatusTone.ERR, "failed")),
                arguments(SessionState.Stopped, look(IconKind.STOPPED, StatusTone.NEUTRAL, "stopped")),
            )

        private fun look(
            icon: IconKind,
            tone: StatusTone,
            key: String,
        ) = Glyph(icon, tone, "state.$key")
    }
}
