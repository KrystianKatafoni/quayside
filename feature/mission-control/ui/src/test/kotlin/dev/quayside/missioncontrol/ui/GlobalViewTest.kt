package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Which states a global view takes (FR-S9; AM-407 plan, table 1). */
internal class GlobalViewTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `view matches the states of its sessions`(
        @Suppress("UNUSED_PARAMETER") name: String,
        view: GlobalView,
        state: SessionState,
        expected: Boolean,
    ) {
        view.matches(state) shouldBe expected
    }

    companion object {
        private val states: Map<String, SessionState> =
            mapOf(
                "AwaitingInput" to SessionState.AwaitingInput("q"),
                "AwaitingPermission" to SessionState.AwaitingPermission("Bash"),
                "AwaitingPermission without tool" to SessionState.AwaitingPermission(null),
                "Working" to SessionState.Working,
                "Done" to SessionState.Done,
                "Failed" to SessionState.Failed,
                "Stopped" to SessionState.Stopped,
            )

        private val taken: Map<GlobalView, Set<String>> =
            mapOf(
                GlobalView.WAITING to setOf("AwaitingInput", "AwaitingPermission", "AwaitingPermission without tool"),
                GlobalView.WORKING to setOf("Working"),
                GlobalView.FAILED to setOf("Failed"),
            )

        @JvmStatic
        fun cases(): List<Arguments> =
            GlobalView.entries.flatMap { view ->
                states.map { (name, state) ->
                    arguments(
                        "$view takes $name: ${name in taken.getValue(view)}",
                        view,
                        state,
                        name in taken.getValue(view),
                    )
                }
            }
    }
}
