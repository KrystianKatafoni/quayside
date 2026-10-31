package dev.quayside.core.process

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class FakeProcessRunnerTest {
    private val version = ShellCommand(listOf("claude", "--version"), Path.of("/tmp"))

    @Test
    fun `succeeds with empty output by default and records calls`() =
        runTest {
            val fake = FakeProcessRunner()
            val options = RunOptions(timeout = 5.seconds)

            fake.run(version, options) shouldBe Outcome.Ok(ProcessResult(0, "", ""))

            fake.calls shouldBe listOf(version to options)
            fake.commands shouldBe listOf(version)
        }

    @Test
    fun `returns the scripted result`() =
        runTest {
            val fake = FakeProcessRunner { Outcome.Err(ProcessError.NotFound(it.argv.first())) }

            fake.run(version) shouldBe Outcome.Err(ProcessError.NotFound("claude"))
        }
}
