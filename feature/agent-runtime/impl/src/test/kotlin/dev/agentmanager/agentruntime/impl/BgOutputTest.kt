package dev.agentmanager.agentruntime.impl

import dev.agentmanager.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class BgOutputTest {
    @ParameterizedTest(name = "[{0}] -> {1}")
    @MethodSource("outputs")
    fun `reads the id of the first backgrounded line`(
        stdout: String,
        expected: String?,
    ) {
        BgOutput.sessionId(stdout) shouldBe expected?.let(::SessionId)
    }

    companion object {
        @JvmStatic
        fun outputs(): List<Arguments> =
            listOf(
                arguments("Starting background service…\nbackgrounded · 7c5dcf5d · x", "7c5dcf5d"),
                arguments("backgrounded · 7c5dcf5d", "7c5dcf5d"),
                arguments("backgrounded · 7c5dcf5d · fix-login\n", "7c5dcf5d"),
                arguments("backgrounded · 11111111 · one\nbackgrounded · 22222222 · two", "11111111"),
                arguments("", null),
                arguments("error", null),
                arguments("Starting background service…\n", null),
            )
    }
}
