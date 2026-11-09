package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.SessionId
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("untrustedOutputs")
    fun `recognizes the workspace not trusted line of stderr`(
        @Suppress("UNUSED_PARAMETER") case: String,
        stderr: String,
        expected: Boolean,
    ) {
        BgOutput.untrusted(stderr) shouldBe expected
    }

    companion object {
        private const val ESC = "\u001b"
        private const val REFUSAL = "Workspace not trusted. Run `claude` in /private/p once, then retry."

        private fun recorded(): String =
            requireNotNull(
                BgOutputTest::class.java.getResource("/fixtures/cli/recorded/2.1.302-bg-untrusted-workspace.stderr"),
            ).readText()

        @JvmStatic
        fun untrustedOutputs(): List<Arguments> =
            listOf(
                arguments("recorded 2.1.302 stderr", recorded(), true),
                arguments("after the service banner", "Starting background service…\n$REFUSAL\n", true),
                arguments("with ANSI around the line", "$ESC[31m$REFUSAL$ESC[0m\r\n", true),
                arguments("indented", "  $REFUSAL", true),
                arguments("lower case", "workspace not trusted. Run claude", false),
                arguments("behind an Error prefix", "Error: $REFUSAL", false),
                arguments("the phrase in the middle of a line", "cannot start: $REFUSAL", false),
                arguments("other failure", "Error: cannot start 'x'", false),
                arguments("empty", "", false),
            )

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
