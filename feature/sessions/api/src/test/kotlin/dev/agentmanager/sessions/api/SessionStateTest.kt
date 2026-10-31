package dev.agentmanager.sessions.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SessionStateTest {
    @ParameterizedTest(name = "{0}: active={1}, waiting={2}")
    @MethodSource("states")
    fun `active and waiting follow the state`(
        state: SessionState,
        active: Boolean,
        waiting: Boolean,
    ) {
        (state.isActive to state.isWaiting) shouldBe (active to waiting)
    }

    private companion object {
        // Named labels: the display name must not depend on toString, which hides texts.
        @JvmStatic
        fun states(): List<Arguments> =
            listOf(
                arguments(named("Working", SessionState.Working), true, false),
                arguments(named("AwaitingInput(null)", SessionState.AwaitingInput(question = null)), true, true),
                arguments(named("AwaitingInput(text)", SessionState.AwaitingInput(question = "Which?")), true, true),
                arguments(
                    named("AwaitingPermission(Bash)", SessionState.AwaitingPermission(tool = "Bash")),
                    true,
                    true,
                ),
                arguments(named("AwaitingPermission(null)", SessionState.AwaitingPermission(tool = null)), true, true),
                arguments(named("Done", SessionState.Done), false, false),
                arguments(named("Failed", SessionState.Failed), false, false),
                arguments(named("Stopped", SessionState.Stopped), false, false),
            )
    }
}
