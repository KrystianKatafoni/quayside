package dev.agentmanager.core.process

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.ByteArrayInputStream

class OutputCaptureTest {
    @ParameterizedTest(name = "{0} bytes, limit {1} -> {2} kept, truncated={3}")
    @CsvSource(
        "0, 10, 0, false",
        "10, 10, 10, false",
        "11, 10, 10, true",
        "20000, 10, 10, true",
        "20000, 9000, 9000, true",
    )
    fun `keeps at most the limit from the start of the stream`(
        size: Int,
        limit: Int,
        kept: Int,
        truncated: Boolean,
    ) {
        val bytes = ByteArray(size) { ('a' + it % 26).code.toByte() }

        val captured = ByteArrayInputStream(bytes).readCapped(limit)

        captured.text shouldBe String(bytes, 0, kept, Charsets.UTF_8)
        captured.truncated shouldBe truncated
    }

    @Test
    fun `a multi-byte character cut at the limit decodes to the replacement character`() {
        val captured = ByteArrayInputStream("aż".toByteArray()).readCapped(2)

        captured.text shouldBe "a�"
        captured.truncated shouldBe true
    }

    @Test
    fun `not found needs exit 127 and a shell message naming the program`() {
        val message = "zsh:1: command not found: claude"

        LoginShellProcessRunner.isNotFound(127, message, "claude") shouldBe true
        LoginShellProcessRunner.isNotFound(1, message, "claude") shouldBe false
        LoginShellProcessRunner.isNotFound(127, ".zprofile:3: command not found: nvm", "claude") shouldBe false
        LoginShellProcessRunner.isNotFound(127, "claude: No such file or directory", "claude") shouldBe false
    }
}
