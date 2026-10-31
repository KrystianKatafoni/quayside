package dev.agentmanager.agentruntime.impl

import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class StderrTailTest {
    @ParameterizedTest(name = "[{0}] -> [{2}]")
    @MethodSource("redactions")
    fun `redacts secrets, home paths and tokens`(
        stderr: String,
        secrets: List<String>,
        expected: String,
    ) {
        StderrTail.of(stderr, secrets) shouldBe expected
    }

    @Test
    fun `keeps only the last 5 lines`() {
        val stderr = (1..20).joinToString("\n") { "line $it" }

        StderrTail.of(stderr, secrets = emptyList()) shouldBe (16..20).joinToString("\n") { "line $it" }
    }

    @Test
    fun `keeps at most 512 characters from the end`() {
        val stderr = "a".repeat(1536) + "b".repeat(512)

        val tail = StderrTail.of(stderr, secrets = emptyList())

        tail.length shouldBeLessThanOrEqual StderrTail.MAX_CHARS
        tail shouldEndWith "b".repeat(100)
    }

    companion object {
        private const val PROMPT = "Fix the login bug"

        @JvmStatic
        fun redactions(): List<Arguments> =
            listOf(
                arguments("failed: $PROMPT", listOf(PROMPT), "failed: ***"),
                arguments("$PROMPT and again $PROMPT", listOf(PROMPT), "*** and again ***"),
                arguments("cannot read /Users/jan/a", emptyList<String>(), "cannot read ~/a"),
                arguments("cannot read /home/jan/a", emptyList<String>(), "cannot read ~/a"),
                arguments("no access to /Users/jan", emptyList<String>(), "no access to ~"),
                arguments("cwd '/home/jan' is not trusted", emptyList<String>(), "cwd '~' is not trusted"),
                arguments("bad path /home/jan.", emptyList<String>(), "bad path ~."),
                arguments("failed: second line here", listOf("first\n  second line here  \nshort"), "failed: ***"),
                arguments("short is kept", listOf("first line of it\nshort"), "short is kept"),
                arguments("Authorization: Bearer abc", emptyList<String>(), "Authorization: ***"),
                arguments("key sk-ant-api03_AbC-9 rejected", emptyList<String>(), "key *** rejected"),
                arguments("\u001b[31merror\u001b[0m\r\n", emptyList<String>(), "error"),
                arguments("plain error", emptyList<String>(), "plain error"),
            )
    }
}
