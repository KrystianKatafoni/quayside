package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeHealth
import dev.quayside.agentruntime.api.RuntimeRequirements.MIN_CLAUDE_VERSION
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests of [ClaudeCliAdapter.health] (plan AM-204, table "Zachowanie health()") on a scripted
 * [FakeProcessRunner]: `--version`, then `daemon status`, then `agents --json`; no process is started.
 */
class ClaudeCliAdapterHealthTest {
    private val home = Path.of("/Users/test")
    private val logger = RecordingLogger()

    private suspend fun health(
        runner: FakeProcessRunner,
        commandTimeout: Duration = 10.seconds,
    ): RuntimeHealth =
        ClaudeCliAdapter(runner, TestClock(), logger, CliConfig(home, EXE, commandTimeout, 30.seconds)).health()

    /** Answers each health command; anything else fails the test through an unexpected answer. */
    private fun claude(
        version: Answer = ok(VERSION_302),
        daemon: Answer = ok(DAEMON_RUNNING),
        agents: Answer = ok("[]\n"),
    ): FakeProcessRunner =
        FakeProcessRunner { command ->
            when (command.argv.drop(1)) {
                listOf("--version") -> version
                listOf("daemon", "status") -> daemon
                listOf("agents", "--json") -> agents
                else -> ok(stderr = "unexpected command", exit = UNEXPECTED_EXIT)
            }
        }

    private fun FakeProcessRunner.verbs(): List<List<String>> = commands.map { it.argv.drop(1) }

    // --- supported installation ---

    @Test
    fun `health of a supported installation is ready`() =
        runTest {
            val runner = claude()

            val health = health(runner)

            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = true, emptyList())
            health.isReady shouldBe true
        }

    @Test
    fun `health reports the parsed version without the suffix`() =
        runTest {
            val health = health(claude(version = ok("\n  2.1.140-beta.1 (Claude Code)\n")))

            health.version shouldBe "2.1.140"
            health.isReady shouldBe true
        }

    // --- step 1: --version ---

    @Test
    fun `missing claude reports only NotInstalled`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.NotFound(EXE)) }

            val health = health(runner)

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.NotInstalled))
            runner.verbs() shouldBe listOf(listOf("--version"))
        }

    @Test
    fun `old version reports UnsupportedVersion and runs no further command`() =
        runTest {
            val runner = claude(version = ok("2.1.100 (Claude Code)\n"))

            val health = health(runner)

            health shouldBe
                RuntimeHealth(
                    version = "2.1.100",
                    agentViewEnabled = false,
                    supervisorRunning = false,
                    problems =
                        listOf(
                            RuntimeError.UnsupportedVersion(found = "2.1.100", required = MIN_CLAUDE_VERSION),
                        ),
                )
            runner.verbs() shouldBe listOf(listOf("--version"))
        }

    @Test
    fun `unparseable version reports a fixed category without the input`() =
        runTest {
            val runner = claude(version = ok("SECRET-VERSION-OUTPUT this is not json {\n"))

            val health = health(runner)

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.Unparseable("version:unparseable")))
            runner.verbs() shouldBe listOf(listOf("--version"))
            assertSoftly {
                health.toString() shouldNotContain "SECRET-VERSION-OUTPUT"
                logger.entries.toString() shouldNotContain "SECRET-VERSION-OUTPUT"
            }
        }

    @Test
    fun `version timeout reports Timeout and stops`() =
        runTest {
            val runner = claude(version = Outcome.Err(ProcessError.Timeout(10.seconds)))

            val health = health(runner)

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.Timeout))
            runner.verbs() shouldBe listOf(listOf("--version"))
        }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("startAndIoFailures")
    fun `version start or io failure reports CommandFailed with a fixed category and stops`(
        error: ProcessError,
        category: String,
    ) = runTest {
        val runner = claude(version = Outcome.Err(error))

        val health = health(runner)

        health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.CommandFailed(-1, category)))
        runner.verbs() shouldBe listOf(listOf("--version"))
    }

    @Test
    fun `version exit failure reports CommandFailed with a redacted tail`() =
        runTest {
            val stderr = "\u001b[31mError: broken config in /Users/jan/.claude\u001b[0m\nkey sk-ant-abc123 rejected\n"
            val runner = claude(version = ok(stderr = stderr, exit = 3))

            val health = health(runner)

            health shouldBe
                RuntimeHealth(
                    version = null,
                    agentViewEnabled = false,
                    supervisorRunning = false,
                    problems =
                        listOf(
                            RuntimeError.CommandFailed(3, "Error: broken config in ~/.claude\nkey *** rejected"),
                        ),
                )
            runner.verbs() shouldBe listOf(listOf("--version"))
        }

    // --- step 3: agents --json ---

    @Test
    fun `disabled Agent View by env reports AgentViewDisabled`() =
        runTest {
            val health = health(claude(agents = ok(stderr = "$DISABLED_BY_ENV\n", exit = 1)))

            health shouldBe
                RuntimeHealth("2.1.302", agentViewEnabled = false, supervisorRunning = true, listOf(AV_DISABLED))
            health.isReady shouldBe false
        }

    @Test
    fun `disabled Agent View by setting reports AgentViewDisabled`() =
        runTest {
            val health = health(claude(agents = ok(stderr = "$DISABLED_BY_SETTING\n", exit = 1)))

            health shouldBe
                RuntimeHealth("2.1.302", agentViewEnabled = false, supervisorRunning = true, listOf(AV_DISABLED))
        }

    @Test
    fun `other agents failure is not reported as AgentViewDisabled`() =
        runTest {
            val stderr = "error: unknown option '--json' in /Users/jan/repo\n"

            val health = health(claude(agents = ok(stderr = stderr, exit = 1)))

            health shouldBe
                RuntimeHealth(
                    version = "2.1.302",
                    agentViewEnabled = false,
                    supervisorRunning = true,
                    problems = listOf(RuntimeError.CommandFailed(1, "error: unknown option '--json' in ~/repo")),
                )
        }

    @Test
    fun `agents timeout reports Timeout`() =
        runTest {
            val health = health(claude(agents = Outcome.Err(ProcessError.Timeout(10.seconds))))

            health shouldBe RuntimeHealth("2.1.302", false, true, listOf(RuntimeError.Timeout))
        }

    @Test
    fun `agents not found reports NotInstalled`() =
        runTest {
            val health = health(claude(agents = Outcome.Err(ProcessError.NotFound(EXE))))

            health shouldBe RuntimeHealth("2.1.302", false, true, listOf(RuntimeError.NotInstalled))
        }

    // --- step 2: daemon status ---

    @Test
    fun `stopped daemon is not a problem`() =
        runTest {
            val health = health(claude(daemon = ok("not running\n", exit = 1)))

            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = false, emptyList())
            health.isReady shouldBe true
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("daemonFailures")
    fun `daemon status failure means not running without a problem`(
        case: String,
        answer: Outcome<ProcessResult, ProcessError>,
    ) = runTest {
        val runner = claude(daemon = answer)

        val health = health(runner)

        withClue(case) {
            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = false, emptyList())
            runner.verbs() shouldBe HEALTH_VERBS
        }
    }

    @Test
    fun `daemon status runs before agents`() =
        runTest {
            val runner = claude()

            health(runner)

            runner.commands.map { it.argv } shouldBe HEALTH_VERBS.map { listOf(EXE) + it }
        }

    // --- every command ---

    @Test
    fun `every health command uses the command timeout and the home dir`() =
        runTest {
            val runner = claude()

            health(runner, commandTimeout = 7.seconds)

            runner.calls.size shouldBe HEALTH_VERBS.size
            runner.calls.forEach { (command, options) ->
                withClue(command.argv) {
                    command shouldBe ShellCommand(command.argv, home)
                    command.env shouldBe emptyMap()
                    options.timeout shouldBe 7.seconds
                }
            }
        }

    @Test
    fun `health log carries no command output`() =
        runTest {
            val runner =
                claude(
                    version = ok("2.1.302 (Claude Code) SECRET-VERSION\n", stderr = "SECRET-VERSION-ERR"),
                    daemon = ok("pid:     698\norigin:  /Users/jan/SECRET-DAEMON\n"),
                    agents = ok(stdout = "SECRET-AGENTS-OUT", stderr = "Error: SECRET-AGENTS-ERR\n", exit = 1),
                )

            health(runner)

            val info = logger.entriesAt(LogLevel.INFO).single()
            val logged = logger.entries.toString()
            assertSoftly {
                info.message shouldBe "claude health"
                info.fields.shouldContainKeys("version", "agentViewEnabled", "supervisorRunning")
                info.fields["version"] shouldBe "2.1.302"
                info.fields["agentViewEnabled"] shouldBe false
                info.fields["supervisorRunning"] shouldBe true
                info.fields.values.toString() shouldContain "CommandFailed"
                logged shouldNotContain "SECRET"
                logged shouldNotContain "/Users/jan"
            }
        }

    companion object {
        private const val EXE = "claude"
        private const val UNEXPECTED_EXIT = 99
        private const val VERSION_302 = "2.1.302 (Claude Code)\n"
        private const val DISABLED_BY_ENV = "'claude agents --json' is disabled by CLAUDE_CODE_DISABLE_AGENT_VIEW."
        private const val DISABLED_BY_SETTING =
            "'claude agents --json' is disabled by the 'disableAgentView' setting."
        private val AV_DISABLED: RuntimeError = RuntimeError.AgentViewDisabled
        private val HEALTH_VERBS = listOf(listOf("--version"), listOf("daemon", "status"), listOf("agents", "--json"))

        /** `claude daemon status` of 2.1.302 while the supervisor runs. */
        private val DAEMON_RUNNING =
            """
            pid:     698
            version: 2.1.302
            uptime:  1m 2s
            origin:  agents
            bg sessions:
              control.sock: reachable
            """.trimIndent() + "\n"

        private fun ok(
            stdout: String = "",
            stderr: String = "",
            exit: Int = 0,
        ): Answer = Outcome.Ok(ProcessResult(exit, stdout, stderr))

        @JvmStatic
        fun startAndIoFailures(): List<Arguments> =
            listOf(
                arguments(ProcessError.StartFailed("/Users/jan/x: No such file"), "process:start-failed"),
                arguments(ProcessError.IoFailed("/Users/jan/x: Broken pipe"), "process:io-failed"),
            )

        @JvmStatic
        fun daemonFailures(): List<Arguments> =
            listOf(
                arguments("unknown command", ok(stderr = "error: unknown command 'daemon'\n", exit = 2)),
                arguments("exit 0 without a pid", ok("starting…\n")),
                arguments("timeout", Outcome.Err(ProcessError.Timeout(10.seconds))),
                arguments("not found", Outcome.Err(ProcessError.NotFound(EXE))),
                arguments("start failed", Outcome.Err(ProcessError.StartFailed("/Users/jan/x"))),
                arguments("io failed", Outcome.Err(ProcessError.IoFailed("/Users/jan/x"))),
            )
    }
}

/** What the scripted runner answers to one command. */
private typealias Answer = Outcome<ProcessResult, ProcessError>
