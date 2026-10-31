package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.PermissionMode
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.agentruntime.api.aStartSpec
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.process.FakeProcessRunner
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessResult
import dev.agentmanager.core.process.ProcessRunner
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.TestClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Unit tests of [ClaudeCliAdapter] on a scripted [FakeProcessRunner]; no process is started. */
class ClaudeCliAdapterTest {
    private val home = Path.of("/Users/test")
    private val clock = TestClock()
    private val logger = RecordingLogger()
    private val id = SessionId(ID)

    private fun adapter(
        runner: ProcessRunner,
        commandTimeout: Duration = 10.seconds,
        startTimeout: Duration = 30.seconds,
    ): AgentRuntime = ClaudeCliAdapter(runner, clock, logger, CliConfig(home, EXE, commandTimeout, startTimeout))

    // --- listSessions ---

    @Test
    fun `listSessions runs agents json all without cwd`() =
        runTest {
            val runner = FakeProcessRunner { ok(document()) }

            adapter(runner, commandTimeout = 7.seconds).listSessions(under = Path.of("/p/a"))

            val (command, options) = runner.calls.single()
            command shouldBe ShellCommand(listOf(EXE, "agents", "--json", "--all"), home)
            options.timeout shouldBe 7.seconds
        }

    @Test
    fun `listSessions filters by path components`() =
        runTest {
            val stdout =
                document(
                    entry("id" to "00000001", "cwd" to "/p/a"),
                    entry("id" to "00000002", "cwd" to "/p/a/sub"),
                    entry("id" to "00000003", "cwd" to "/p/ab"),
                    entry("id" to "00000004", "cwd" to "/q"),
                )
            val runtime = adapter(FakeProcessRunner { ok(stdout) })

            val under = runtime.listSessions(under = Path.of("/p/a")).getOrNull().shouldNotBeNull()
            val all = runtime.listSessions(under = null).getOrNull().shouldNotBeNull()

            under.map { it.id.value } shouldContainExactlyInAnyOrder listOf("00000001", "00000002")
            all.map { it.id.value } shouldContainExactly listOf("00000001", "00000002", "00000003", "00000004")
        }

    @ParameterizedTest(name = "[{0}] -> {1}")
    @MethodSource("unusableListings")
    fun `listSessions passes parser errors through`(
        stdout: String,
        reason: String,
    ) = runTest {
        adapter(FakeProcessRunner { ok(stdout) }).listSessions() shouldBe Outcome.Err(RuntimeError.Unparseable(reason))
    }

    @Test
    fun `listSessions uses the clock for a missing startedAt`() =
        runTest {
            val observed = Instant.parse("2026-10-18T10:15:00Z")
            clock.setTo(observed)
            val runtime = adapter(FakeProcessRunner { ok(document(entry("startedAt" to Absent))) })

            runtime
                .listSessions()
                .getOrNull()
                .shouldNotBeNull()
                .single()
                .startedAt shouldBe observed
        }

    @Test
    fun `parser notices are logged at debug without input text`() =
        runTest {
            val stdout =
                document(
                    entry("kind" to "interactive", "id" to Absent, "name" to "TOP-SECRET-NAME"),
                    entry("startedAt" to Absent, "state" to "Secret Phase", "cwd" to "/Users/jan/secret-project"),
                )

            adapter(FakeProcessRunner { ok(stdout) }).listSessions()

            logger.entriesAt(LogLevel.DEBUG).shouldNotBeEmpty()
            val logged = logger.entries.toString()
            assertSoftly {
                logged shouldNotContain "TOP-SECRET-NAME"
                logged shouldNotContain "secret-project"
                logged shouldNotContain "/Users/jan"
            }
        }

    @Test
    fun `failing agents returns CommandFailed with the stderr tail`() =
        runTest {
            val runtime =
                adapter(FakeProcessRunner { ok(stderr = "\u001b[31mError: supervisor down\u001b[0m", exit = 2) })

            runtime.listSessions() shouldBe Outcome.Err(RuntimeError.CommandFailed(2, "Error: supervisor down"))
        }

    // --- start ---

    @Test
    fun `start builds claude bg with every flag of the spec`() =
        runTest {
            val runner = FakeProcessRunner { ok(BACKGROUNDED) }
            val spec =
                StartSpec(
                    cwd = PROJECT,
                    prompt = "Fix the login bug",
                    name = "fix-login",
                    agent = "reviewer",
                    model = "opus",
                    permissionMode = PermissionMode.PLAN,
                )

            val started = adapter(runner).start(spec)

            started shouldBe Outcome.Ok(id)
            runner.commands.single().argv shouldBe
                listOf(
                    EXE,
                    "--bg",
                    "--name",
                    "fix-login",
                    "--agent",
                    "reviewer",
                    "--model",
                    "opus",
                    "--permission-mode",
                    "plan",
                    "--",
                    "Fix the login bug",
                )
        }

    @Test
    fun `start without optional fields passes only bg and the prompt`() =
        runTest {
            val runner = FakeProcessRunner { ok(BACKGROUNDED) }

            adapter(runner).start(StartSpec(cwd = PROJECT, prompt = "Fix the login bug"))

            runner.commands.single().argv shouldBe listOf(EXE, "--bg", "--", "Fix the login bug")
        }

    @Test
    fun `start puts the prompt after a double dash`() =
        runTest {
            val runner = FakeProcessRunner { ok(BACKGROUNDED) }
            val prompt = "--help -p \"quoted\" \$HOME\nsecond line"

            adapter(runner).start(aStartSpec(cwd = PROJECT, prompt = prompt))

            runner.commands
                .single()
                .argv
                .takeLast(2) shouldBe listOf("--", prompt)
        }

    @Test
    fun `start runs in the spec cwd with the start timeout`() =
        runTest {
            val runner = FakeProcessRunner { ok(BACKGROUNDED) }

            adapter(runner, commandTimeout = 7.seconds, startTimeout = 45.seconds).start(aStartSpec(cwd = PROJECT))

            val (command, options) = runner.calls.single()
            command.workDir shouldBe PROJECT
            command.env shouldBe emptyMap()
            options.timeout shouldBe 45.seconds
        }

    @Test
    fun `start reads the id after the background service banner`() =
        runTest {
            val runtime = adapter(FakeProcessRunner { ok("Starting background service…\n$BACKGROUNDED\n") })

            runtime.start(aStartSpec(cwd = PROJECT)) shouldBe Outcome.Ok(id)
        }

    @Test
    fun `start without a backgrounded line returns Unparseable`() =
        runTest {
            val runtime = adapter(FakeProcessRunner { ok("Starting background service…\n") })

            runtime.start(aStartSpec(cwd = PROJECT)) shouldBe Outcome.Err(RuntimeError.Unparseable("bg-output:no-id"))
        }

    // --- commands by id ---

    @ParameterizedTest
    @EnumSource(IdOperation::class)
    fun `id command runs claude verb id in the home dir with the command timeout`(operation: IdOperation) =
        runTest {
            val runner = FakeProcessRunner { ok() }

            val outcome = operation.run(adapter(runner, commandTimeout = 7.seconds), id)

            outcome.shouldBeInstanceOf<Outcome.Ok<*>>()
            val (command, options) = runner.calls.first()
            command shouldBe ShellCommand(listOf(EXE, operation.verb, ID), home)
            options.timeout shouldBe 7.seconds
        }

    @ParameterizedTest
    @EnumSource(IdOperation::class)
    fun `failed id command returns UnknownSession when the id is not listed`(operation: IdOperation) =
        runTest {
            val runner =
                FakeProcessRunner { command ->
                    if (command.argv[1] == "agents") {
                        ok(document(entry("id" to "a1b2c3d4")))
                    } else {
                        ok(stderr = "Error: no session $ID", exit = 1)
                    }
                }

            operation.run(adapter(runner), id) shouldBe Outcome.Err(RuntimeError.UnknownSession(id))
            runner.commands.map { it.argv[1] } shouldBe listOf(operation.verb, "agents")
        }

    @ParameterizedTest
    @EnumSource(IdOperation::class)
    fun `failed id command returns CommandFailed when the id is listed`(operation: IdOperation) =
        runTest {
            val runner =
                FakeProcessRunner { command ->
                    if (command.argv[1] == "agents") ok(document(entry())) else ok(stderr = "Error: busy\n", exit = 2)
                }

            operation.run(adapter(runner), id) shouldBe Outcome.Err(RuntimeError.CommandFailed(2, "Error: busy"))
        }

    @Test
    fun `rm that keeps the session returns CommandFailed`() =
        runTest {
            val kept = "kept $ID: worktree .claude/worktrees/fix-login has unpushed commits\n"
            val runtime = adapter(FakeProcessRunner { ok(stdout = kept) })

            val error = runtime.remove(id).errorOrNull().shouldBeInstanceOf<RuntimeError.CommandFailed>()

            error.exitCode shouldBe 0
            error.stderrTail shouldContain "kept $ID"
        }

    @Test
    fun `recentOutput returns the cleaned tail of claude logs`() =
        runTest {
            val runtime = adapter(FakeProcessRunner { ok("\u001b[32mRunning tests...\u001b[0m\r\nAll green\n\n") })

            runtime.recentOutput(id) shouldBe Outcome.Ok("Running tests...\nAll green")
        }

    @Test
    fun `recentOutput reads up to 4 MiB and warns when the output was cut`() =
        runTest {
            val runner =
                FakeProcessRunner { Outcome.Ok(ProcessResult(0, "SENSITIVE-OUTPUT", "", stdoutTruncated = true)) }

            adapter(runner).recentOutput(id)

            runner.calls
                .single()
                .second.maxOutputBytes shouldBe 4 * 1024 * 1024
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
            logger.entriesAt(LogLevel.WARN).toString() shouldNotContain "SENSITIVE-OUTPUT"
        }

    // --- process errors ---

    @ParameterizedTest
    @EnumSource(Operation::class)
    fun `process NotFound maps to NotInstalled`(operation: Operation) =
        runTest {
            val runtime = adapter(FakeProcessRunner { Outcome.Err(ProcessError.NotFound(EXE)) })

            operation.run(runtime, id) shouldBe Outcome.Err(RuntimeError.NotInstalled)
        }

    @ParameterizedTest
    @EnumSource(Operation::class)
    fun `process Timeout maps to Timeout`(operation: Operation) =
        runTest {
            val runtime = adapter(FakeProcessRunner { Outcome.Err(ProcessError.Timeout(10.seconds)) })

            operation.run(runtime, id) shouldBe Outcome.Err(RuntimeError.Timeout)
        }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("startAndIoFailures")
    fun `StartFailed and IoFailed map to CommandFailed with a fixed category`(
        operation: Operation,
        error: ProcessError,
        category: String,
    ) = runTest {
        val outcome = operation.run(adapter(FakeProcessRunner { Outcome.Err(error) }), id)

        outcome shouldBe Outcome.Err(RuntimeError.CommandFailed(-1, category))
    }

    // --- attach, privacy, factory (health: ClaudeCliAdapterHealthTest) ---

    @Test
    fun `attachCommand is claude attach id in the home dir`() {
        val runner = FakeProcessRunner()

        adapter(runner).attachCommand(id) shouldBe ShellCommand(listOf(EXE, "attach", ID), home)

        runner.calls.shouldBeEmpty()
    }

    @Test
    fun `prompt never appears in logs or errors`() =
        runTest {
            val prompt = "launch codes 0000 for project falcon"
            val succeeding = adapter(FakeProcessRunner { ok(BACKGROUNDED) })
            val failing = adapter(FakeProcessRunner { ok(stderr = "Error: cannot run '$prompt'\n", exit = 1) })

            succeeding.start(aStartSpec(cwd = PROJECT, prompt = prompt)) shouldBe Outcome.Ok(id)
            val error = failing.start(aStartSpec(cwd = PROJECT, prompt = prompt)).errorOrNull()

            val failed = error.shouldBeInstanceOf<RuntimeError.CommandFailed>()
            assertSoftly {
                failed.exitCode shouldBe 1
                failed.stderrTail shouldNotContain prompt
                failed.stderrTail shouldContain "***"
                logger.entries.toString() shouldNotContain prompt
                logger.entries.toString() shouldNotContain "falcon"
            }
        }

    @Test
    fun `agentRuntimeModule uses claude and the default timeouts`() =
        runTest {
            val runner =
                FakeProcessRunner { command -> if (command.argv[1] == "agents") ok(document()) else ok(BACKGROUNDED) }
            val runtime = agentRuntimeModule(runner, clock, logger, home)

            runtime.listSessions()
            runtime.start(aStartSpec(cwd = PROJECT))

            val (list, start) = runner.calls
            list.first.argv.first() shouldBe "claude"
            list.second.timeout shouldBe 10.seconds
            start.first.argv.first() shouldBe "claude"
            start.second.timeout shouldBe 30.seconds
        }

    /** The four operations that take an id, with the `claude` verb each one runs. */
    enum class IdOperation(
        val verb: String,
    ) {
        STOP("stop") {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.stop(id)
        },
        RESPAWN("respawn") {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.respawn(id)
        },
        REMOVE("rm") {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.remove(id)
        },
        RECENT_OUTPUT("logs") {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.recentOutput(id)
        },
        ;

        abstract suspend fun run(
            runtime: AgentRuntime,
            id: SessionId,
        ): Outcome<*, RuntimeError>
    }

    /** Every operation that runs a process. */
    enum class Operation {
        LIST {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.listSessions()
        },
        START {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.start(aStartSpec(cwd = PROJECT))
        },
        STOP {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.stop(id)
        },
        RESPAWN {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.respawn(id)
        },
        REMOVE {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.remove(id)
        },
        RECENT_OUTPUT {
            override suspend fun run(
                runtime: AgentRuntime,
                id: SessionId,
            ) = runtime.recentOutput(id)
        },
        ;

        abstract suspend fun run(
            runtime: AgentRuntime,
            id: SessionId,
        ): Outcome<*, RuntimeError>
    }

    companion object {
        private const val EXE = "claude"
        private const val BACKGROUNDED = "backgrounded · $ID · fix-login"
        private val PROJECT: Path = Path.of("/Users/test/projects/agent-manager")

        private fun ok(
            stdout: String = "",
            stderr: String = "",
            exit: Int = 0,
        ): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(exit, stdout, stderr))

        @JvmStatic
        fun unusableListings(): List<Arguments> =
            listOf(
                arguments("", UnparseableReason.EMPTY),
                arguments("Error: supervisor not running", UnparseableReason.MALFORMED),
                arguments("{}", UnparseableReason.NOT_AN_ARRAY),
            )

        @JvmStatic
        fun startAndIoFailures(): List<Arguments> =
            Operation.entries.flatMap { operation ->
                listOf(
                    arguments(
                        operation,
                        ProcessError.StartFailed("/Users/jan/x: No such file"),
                        "process:start-failed",
                    ),
                    arguments(operation, ProcessError.IoFailed("/Users/jan/x: Broken pipe"), "process:io-failed"),
                )
            }
    }
}
