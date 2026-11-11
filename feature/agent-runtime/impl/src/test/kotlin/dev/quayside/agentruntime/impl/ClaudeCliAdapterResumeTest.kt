package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ResumeSpec
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.errorOrNull
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

/** `ClaudeCliAdapter.resume` on a scripted runner and on the output recorded from Claude Code 2.1.322. */
class ClaudeCliAdapterResumeTest {
    private val home = Path.of("/Users/test")
    private val logger = RecordingLogger()
    private val spec = ResumeSpec(ConversationId(UUID), PROJECT)

    private fun adapter(
        runner: FakeProcessRunner,
        tokens: HookTokenStore? = null,
    ): AgentRuntime = ClaudeCliAdapter(runner, TestClock(), logger, CliConfig(home, EXE, 7.seconds, 45.seconds, tokens))

    private fun recorded(name: String): String =
        Path
            .of(checkNotNull(javaClass.getResource("/fixtures/cli/recorded/$name")) { "missing $name" }.toURI())
            .readText()

    private fun replay(prefix: String): FakeProcessRunner =
        FakeProcessRunner {
            Outcome.Ok(
                ProcessResult(
                    recorded("$prefix.exit").trim().toInt(),
                    recorded("$prefix.stdout"),
                    recorded("$prefix.stderr"),
                ),
            )
        }

    @Test
    fun `resume reads the id from the idle line`() =
        runTest {
            val runner = replay("2.1.322-bg-resume-no-prompt")

            adapter(runner).resume(spec) shouldBe Outcome.Ok(SessionId("00000001"))

            runner.commands.single().argv shouldBe listOf(EXE, "--bg", "--resume", UUID)
        }

    @Test
    fun `resume of an open conversation returns the id of the copy`() =
        runTest {
            adapter(replay("2.1.322-bg-resume-open-elsewhere")).resume(spec) shouldBe Outcome.Ok(SessionId("00000002"))
        }

    @Test
    fun `resume of an unknown conversation is Ok with the id`() =
        runTest {
            // The CLI answers 0 for an unknown UUID (compat M9): the check belongs to the caller.
            adapter(replay("2.1.322-bg-resume-unknown-session")).resume(spec) shouldBe Outcome.Ok(SessionId("00000000"))
        }

    @Test
    fun `resume in an untrusted directory is WorkspaceNotTrusted`() =
        runTest {
            val stderr = recorded("2.1.302-bg-untrusted-workspace.stderr")
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(1, "", stderr)) }

            adapter(runner).resume(spec) shouldBe Outcome.Err(RuntimeError.WorkspaceNotTrusted)
        }

    @Test
    fun `resume runs in the spec directory`() =
        runTest {
            val runner = replay("2.1.322-bg-resume-no-prompt")

            adapter(runner).resume(spec)

            runner.commands.single().workDir shouldBe PROJECT
        }

    @Test
    fun `resume passes the hook token like start`() =
        runTest {
            val runner = replay("2.1.322-bg-resume-no-prompt")
            val store = HookTokenStore { Outcome.Ok(HookToken(TOKEN)) }

            adapter(runner, store).resume(spec)

            runner.commands.single().env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN)
        }

    @Test
    fun `resume without a token store has an empty environment`() =
        runTest {
            val runner = replay("2.1.322-bg-resume-no-prompt")

            adapter(runner).resume(spec)

            runner.commands.single().env shouldBe emptyMap()
        }

    @Test
    fun `resume uses the start timeout`() =
        runTest {
            val runner = replay("2.1.322-bg-resume-no-prompt")

            adapter(runner).resume(spec)

            runner.calls
                .single()
                .second.timeout shouldBe 45.seconds
        }

    @Test
    fun `resume without a backgrounded line is Unparseable`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(0, "Starting background service…\n", "")) }

            adapter(runner).resume(spec) shouldBe Outcome.Err(RuntimeError.Unparseable("bg-output:no-id"))
        }

    @Test
    fun `other resume failure is CommandFailed with the exit code`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(2, "", "Error: boom\n")) }

            val error = adapter(runner).resume(spec).errorOrNull()

            error.shouldBeInstanceOf<RuntimeError.CommandFailed>().exitCode shouldBe 2
        }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("processFailures")
    fun `resume maps process failures to fixed categories`(
        failure: ProcessError,
        expected: RuntimeError,
    ) = runTest {
        val runner = FakeProcessRunner { Outcome.Err(failure) }

        adapter(runner).resume(spec) shouldBe Outcome.Err(expected)
    }

    @Test
    fun `resume logs no stderr text`() =
        runTest {
            val stderr = recorded("2.1.322-bg-resume-open-elsewhere.stderr")
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(0, "backgrounded · 00000002\n", stderr)) }

            adapter(runner).resume(spec)

            val logged = logger.entries.toString()
            logged shouldNotContain "open in another Claude Code process"
            logged shouldNotContain PROJECT.toString()
            logged shouldNotContain UUID
            logger.entriesAt(LogLevel.WARN) shouldBe emptyList()
        }

    private companion object {
        const val EXE = "claude"
        const val UUID = "5e2a9c41-0000-4000-8000-0000000000aa"
        const val TOKEN = "tok-123"
        val PROJECT: Path = Path.of("/Users/test/projects/agent-manager")

        @JvmStatic
        fun processFailures(): List<Arguments> =
            listOf(
                arguments(ProcessError.NotFound("claude"), RuntimeError.NotInstalled),
                arguments(ProcessError.Timeout(45.seconds), RuntimeError.Timeout),
                arguments(
                    ProcessError.StartFailed("/Users/jan/x"),
                    RuntimeError.CommandFailed(-1, "process:start-failed"),
                ),
            )
    }
}
