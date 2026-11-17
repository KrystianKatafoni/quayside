package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.RuntimeRequirements
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * The config dir of an extra account is masked in the error of every failing path: `rm` (refused with `kept`),
 * `respawn`, the Agent View probe of `health` and the one-shot prompt (plan AM-1003, ENG-C8). Each case fails when
 * its `env` is not passed to the mask.
 */
class ClaudeAccountMaskTest {
    private val home = Path.of("/Users/test")
    private val configDir = Path.of("/srv/claude-accounts/work")
    private val work = AccountId("work")
    private val id = SessionId("a1b2c3d4")
    private val leak = "cannot use $configDir/x"

    private fun runner(
        exit: Int,
        stdout: String,
        stderr: String,
    ) = FakeProcessRunner { Outcome.Ok(ProcessResult(exit, stdout, stderr)) }

    private fun runtime(runner: FakeProcessRunner) =
        agentRuntimeModule(
            process = runner,
            clock = TestClock(),
            logger = NoOpLogger,
            homeDir = home,
            account = work,
            configDir = configDir,
        )

    @Test
    fun `rm refused with kept masks the config dir`() =
        runTest {
            val result = runtime(runner(0, "kept ${id.value} $configDir/worktree", "")).remove(id)

            result.toString() shouldContain "kept"
            result.toString() shouldNotContain configDir.toString()
        }

    @Test
    fun `a failing respawn masks the config dir`() =
        runTest {
            val result = runtime(runner(1, "", leak)).respawn(id)

            result.toString() shouldContain "cannot use"
            result.toString() shouldNotContain configDir.toString()
        }

    @Test
    fun `a failing Agent View probe in health masks the config dir`() =
        runTest {
            val probe =
                FakeProcessRunner { command ->
                    when {
                        command.argv.contains("--version") -> {
                            Outcome.Ok(ProcessResult(0, "${RuntimeRequirements.MIN_CLAUDE_VERSION} (Claude Code)", ""))
                        }

                        command.argv.contains("agents") -> {
                            Outcome.Ok(ProcessResult(1, "", leak))
                        }

                        else -> {
                            Outcome.Ok(ProcessResult(0, "", ""))
                        }
                    }
                }

            val health = runtime(probe).health()

            health.problems.toString() shouldContain "cannot use"
            health.problems.toString() shouldNotContain configDir.toString()
        }

    @Test
    fun `a failing one-shot prompt masks the config dir`() =
        runTest {
            val prompt = oneShotPromptModule(runner(1, "", leak), home, account = work, configDir = configDir)

            val result = prompt.ask(PromptRequest("Summarize", "haiku", 10.seconds))

            result.toString() shouldContain "cannot use"
            result.toString() shouldNotContain configDir.toString()
            (result is Outcome.Err) shouldBe true
        }
}
