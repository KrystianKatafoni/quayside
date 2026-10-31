package dev.quayside.terminal.impl.replyspike

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val DIR = Path.of("/tmp/am507/1")
private const val SECRET = "SECRET_OUTPUT_TEXT"

private fun ok(stdout: String = "") = Outcome.Ok(ProcessResult(0, stdout, ""))

private fun listing(runner: (ShellCommand) -> String) =
    FakeProcessRunner { command ->
        ok(if (command.argv.getOrNull(1) == "agents") runner(command) else "")
    }

/** `SpikeClaude` over [FakeProcessRunner]: the CLI invocations of the runner, no real `claude`. */
internal class SpikeClaudeTest {
    @Test
    fun `start runs claude bg with name model and prompt in the dir`() =
        runTest {
            val runner = FakeProcessRunner { ok("backgrounded · 00000002\n") }

            val result = SpikeClaude(runner).start("am507-t-1", "Zapytaj mnie o słowo", DIR)

            result shouldBe Outcome.Ok(Unit)
            runner.commands shouldBe
                listOf(
                    ShellCommand(
                        argv =
                            listOf(
                                "claude",
                                "--bg",
                                "--name",
                                "am507-t-1",
                                "--model",
                                "haiku",
                                "--",
                                "Zapytaj mnie o słowo",
                            ),
                        workDir = DIR,
                    ),
                )
        }

    @Test
    fun `start uses the given executable and model`() =
        runTest {
            val runner = FakeProcessRunner()

            SpikeClaude(runner, executable = "/opt/claude").start("n", "p", DIR, model = "sonnet")

            runner.commands.single().argv shouldBe
                listOf("/opt/claude", "--bg", "--name", "n", "--model", "sonnet", "--", "p")
        }

    @Test
    fun `find reads id phase and waitingFor by name`() =
        runTest {
            val runner = listing { replySpikeResource("agents-blocked.json") }

            val result = SpikeClaude(runner).find("agent-manager-2")

            result shouldBe Outcome.Ok(SpikeSession("00000002", SpikePhase.BLOCKED, "permission prompt"))
            runner.commands.single().argv shouldBe listOf("claude", "agents", "--json", "--all")
        }

    @Test
    fun `find reads a working session without waitingFor`() =
        runTest {
            val runner = listing { replySpikeResource("agents-working.json") }

            SpikeClaude(runner).find("agent-manager-2") shouldBe
                Outcome.Ok(SpikeSession("00000002", SpikePhase.WORKING, null))
        }

    @Test
    fun `find returns null for an unknown name`() =
        runTest {
            val runner = listing { replySpikeResource("agents-working.json") }

            SpikeClaude(runner).find("no-such-session") shouldBe Outcome.Ok(null)
        }

    @Test
    fun `find does not return an interactive session`() =
        runTest {
            val runner = listing { replySpikeResource("agents-working.json") }

            SpikeClaude(runner).find("agent-manager-4") shouldBe Outcome.Ok(null)
        }

    @Test
    fun `unparseable listing is an error category without its text`() =
        runTest {
            val runner = listing { "not json $SECRET" }

            val result = SpikeClaude(runner).find("agent-manager-2")

            result shouldBe Outcome.Err(SpikeError.UNPARSEABLE)
            result.toString() shouldNotContain SECRET
        }

    @Test
    fun `stop and remove runs both commands`() =
        runTest {
            val runner = FakeProcessRunner()

            val result = SpikeClaude(runner).stopAndRemove("00000002")

            result shouldBe Outcome.Ok(Unit)
            runner.commands.map { it.argv } shouldBe
                listOf(listOf("claude", "stop", "00000002"), listOf("claude", "rm", "00000002"))
        }

    @ParameterizedTest(name = "state {0}, waitingFor {1}")
    @MethodSource("phases")
    fun `phase follows the agent runtime parser`(
        state: String?,
        waitingFor: String?,
        expected: SpikePhase,
    ) = runTest {
        val entry =
            listOfNotNull(
                """"id":"7"""",
                """"kind":"background"""",
                """"name":"n"""",
                state?.let { """"state":"$it"""" },
                waitingFor?.let { """"waitingFor":"$it"""" },
            ).joinToString(",", "[{", "}]")
        val runner = listing { entry }

        SpikeClaude(runner).find("n").getOrNull()?.phase shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    @Suppress("UNUSED_PARAMETER")
    fun `a failing command is its category`(
        name: String,
        failure: Outcome<ProcessResult, ProcessError>,
        expected: SpikeError,
    ) = runTest {
        val claude = SpikeClaude(FakeProcessRunner { failure })

        val result = claude.start("n", "p", DIR)

        result shouldBe Outcome.Err(expected)
        result.toString() shouldNotContain SECRET
        claude.stopAndRemove("1") shouldBe Outcome.Err(expected)
        claude.find("n") shouldBe Outcome.Err(expected)
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun phases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("working", null, SpikePhase.WORKING),
                Arguments.of("BUSY ", null, SpikePhase.WORKING),
                Arguments.of("blocked", "permission prompt", SpikePhase.BLOCKED),
                Arguments.of("needs-input", null, SpikePhase.BLOCKED),
                Arguments.of("done", null, SpikePhase.DONE),
                Arguments.of("failed", null, SpikePhase.FAILED),
                Arguments.of("stopped", null, SpikePhase.STOPPED),
                Arguments.of(null, "permission prompt", SpikePhase.BLOCKED),
                Arguments.of("something-new", "input", SpikePhase.BLOCKED),
                Arguments.of(null, null, SpikePhase.WORKING),
                Arguments.of("something-new", null, SpikePhase.WORKING),
                Arguments.of("done", "input", SpikePhase.BLOCKED),
                Arguments.of("idle", "idle_prompt", SpikePhase.BLOCKED),
                Arguments.of("done", "input_needed", SpikePhase.BLOCKED),
            )

        @JvmStatic
        fun failures(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "non-zero exit code",
                    Outcome.Ok(ProcessResult(1, SECRET, SECRET)),
                    SpikeError.COMMAND_FAILED,
                ),
                Arguments.of("program not found", Outcome.Err(ProcessError.NotFound("claude")), SpikeError.NOT_FOUND),
                Arguments.of("timeout", Outcome.Err(ProcessError.Timeout(30.seconds)), SpikeError.TIMEOUT),
                Arguments.of("start failed", Outcome.Err(ProcessError.StartFailed(SECRET)), SpikeError.START_FAILED),
                Arguments.of("io failed", Outcome.Err(ProcessError.IoFailed(SECRET)), SpikeError.IO_FAILED),
            )
    }
}
