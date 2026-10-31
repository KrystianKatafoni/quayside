package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The "last event" column of a session row (FR-S1). */
internal class LastEventTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("events")
    fun `last event follows the state`(
        state: SessionState,
        expected: UiText,
    ) {
        LastEvent.of(state) shouldBe expected
    }

    @Test
    fun `the question of a waiting session is not part of the event text`() {
        LastEvent.of(SessionState.AwaitingInput("secret question")).toString() shouldNotContain "secret question"
    }

    companion object {
        @JvmStatic
        fun events(): List<Arguments> =
            listOf(
                arguments(SessionState.AwaitingPermission("Bash"), UiText("event.permission", listOf("Bash"))),
                arguments(SessionState.AwaitingPermission(null), UiText("event.permission.unknown")),
                arguments(SessionState.AwaitingInput("Which branch?"), UiText("event.input")),
                arguments(SessionState.AwaitingInput(null), UiText("event.input")),
                arguments(SessionState.Working, UiText("event.working")),
                arguments(SessionState.Done, UiText("event.done")),
                arguments(SessionState.Failed, UiText("event.failed")),
                arguments(SessionState.Stopped, UiText("event.stopped")),
            )
    }
}
