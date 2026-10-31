package dev.quayside.agentruntime.impl

import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class OutputTailTest {
    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @MethodSource("cleanups")
    fun `removes terminal sequences and control characters and trims the end`(
        stdout: String,
        expected: String,
    ) {
        OutputTail.of(stdout) shouldBe expected
    }

    @Test
    fun `keeps only the last 200 lines`() {
        val stdout = (1..500).joinToString("\n") { "line $it" }

        OutputTail.of(stdout).lines() shouldBe (301..500).map { "line $it" }
    }

    @Test
    fun `keeps at most 16 KiB from the end`() {
        val stdout = (1..100).joinToString("\n") { "$it:" + "x".repeat(1000) } + "\nlast line"

        val tail = OutputTail.of(stdout)

        tail.length shouldBeLessThanOrEqual OutputTail.MAX_CHARS
        tail shouldEndWith "x\nlast line"
        stdout shouldEndWith tail
    }

    companion object {
        @JvmStatic
        fun cleanups(): List<Arguments> =
            listOf(
                arguments("\u001b[31mred\u001b[0m text", "red text"),
                arguments("\u001b[2K\u001b[1Gprogress", "progress"),
                arguments("\u001b]0;claude\u0007ready", "ready"),
                arguments("x\n\n", "x"),
                arguments("a\tb\u0007c\r\nd", "a\tbc\nd"),
                arguments("", ""),
            )
    }
}
