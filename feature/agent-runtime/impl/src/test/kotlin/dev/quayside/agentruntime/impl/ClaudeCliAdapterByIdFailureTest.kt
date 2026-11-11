package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** A failed command by id when `claude agents` fails too: the id cannot be called unknown (AM-313 review). */
class ClaudeCliAdapterByIdFailureTest {
    private val id = SessionId("a1b2c3d4")

    @Test
    fun `failed id command returns CommandFailed when the listing is not available`() =
        runTest {
            val runner =
                FakeProcessRunner { command ->
                    val stderr = if (command.argv[1] == "agents") "Error: down\n" else "Error: busy\n"
                    Outcome.Ok(ProcessResult(if (command.argv[1] == "agents") 1 else 2, "", stderr))
                }
            val config = CliConfig(Path.of("/Users/test"), "claude", 10.seconds, 30.seconds)

            val outcome = ClaudeCliAdapter(runner, TestClock(), RecordingLogger(), config).stop(id)

            // a failed listing says nothing about the id, so it must not become UnknownSession
            outcome shouldBe Outcome.Err(RuntimeError.CommandFailed(2, "Error: busy"))
            runner.commands.map { it.argv[1] } shouldBe listOf("stop", "agents")
        }
}
