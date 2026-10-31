package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.errorOrNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The helpers other features' tests use on [FakeTerminalApi]. */
class FakeTerminalApiTest {
    @TempDir
    lateinit var dir: Path

    private val api = FakeTerminalApi()

    @Test
    fun `sent records typed text in order`() =
        runTest {
            val session = api.openShell(dir).shouldBeInstanceOf<Outcome.Ok<TerminalSession>>().value

            session.send("a")
            session.send("b\r")

            api.sessions.single().sent shouldBe listOf("a", "b\r")
        }

    @Test
    fun `finish sets the exit code and send then returns Closed`() =
        runTest {
            val session = api.openShell(dir).shouldBeInstanceOf<Outcome.Ok<TerminalSession>>().value

            api.sessions.single().finish(7)

            session.exitCode.value shouldBe 7
            session.send("x").errorOrNull() shouldBe TerminalError.Closed
        }

    @Test
    fun `commands records the opened commands`() =
        runTest {
            val command = ShellCommand(listOf("claude", "attach", "s-1"), dir)

            api.openCommand(command)

            api.commands shouldBe listOf(command)
        }
}
