package dev.agentmanager.core.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ShellCommandTest {
    private val dir = Path.of("/tmp/project")

    @Test
    fun `requires a program name`() {
        shouldThrow<IllegalArgumentException> { ShellCommand(emptyList(), dir) }
        shouldThrow<IllegalArgumentException> { ShellCommand(listOf(""), dir) }
    }

    @Test
    fun `toString hides environment values`() {
        val command = ShellCommand(listOf("claude", "agents", "--json"), dir, mapOf("AM_TOKEN" to "s3cr3t"))

        val text = command.toString()

        text shouldContain "AM_TOKEN"
        text shouldContain "[claude, agents, --json]"
        text shouldNotContain "s3cr3t"
    }

    @Test
    fun `equality includes environment`() {
        ShellCommand(listOf("ls"), dir, mapOf("A" to "1")) shouldBe ShellCommand(listOf("ls"), dir, mapOf("A" to "1"))
        (ShellCommand(listOf("ls"), dir) == ShellCommand(listOf("ls"), dir, mapOf("A" to "1"))) shouldBe false
    }
}
