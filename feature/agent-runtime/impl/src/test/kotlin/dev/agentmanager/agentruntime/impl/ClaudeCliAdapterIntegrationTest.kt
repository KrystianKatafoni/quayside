package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.PermissionMode
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.TestClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ClaudeCliAdapter] through the real [LoginShellProcessRunner] on the fake `claude` (docs/12 §4):
 * real processes, real but short timeouts, state in a temp dir.
 */
class ClaudeCliAdapterIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private lateinit var claude: FakeClaude

    @AfterEach
    fun cleanUp() {
        if (::claude.isInitialized) killAll(claude.pids("bg-child.pid") + claude.pids("hang.pid"))
        io.close()
    }

    private val project: Path get() = dir.resolve("project").createDirectories().toRealPath()

    private fun runtime(
        world: RuntimeWorld = RuntimeWorld(),
        commandTimeout: Duration = 10.seconds,
        startTimeout: Duration = 10.seconds,
    ): AgentRuntime {
        claude = FakeClaude.install(dir.resolve("claude"), world)
        return agentRuntimeModule(
            process = LoginShellProcessRunner(io, TEST_SHELL),
            clock = TestClock(),
            logger = logger,
            homeDir = dir.resolve("home").createDirectories(),
            executable = claude.executable,
            commandTimeout = commandTimeout,
            startTimeout = startTimeout,
        )
    }

    @Test
    fun `start passes the spec flags to claude`() =
        runTest {
            val runtime = runtime()
            val spec =
                StartSpec(
                    cwd = project,
                    prompt = "Fix the login bug",
                    name = "fix-login",
                    agent = "reviewer",
                    model = "opus",
                    permissionMode = PermissionMode.PLAN,
                )

            val id = runtime.start(spec).getOrNull().shouldNotBeNull()

            claude.calls().single() shouldBe EXPECTED_START_ARGS
            assertSoftly(
                runtime
                    .listSessions()
                    .getOrNull()
                    .shouldNotBeNull()
                    .single(),
            ) {
                this.id shouldBe id
                name shouldBe "fix-login"
                cwd shouldBe project
            }
        }

    @Test
    fun `garbage from agents returns Unparseable`() =
        runTest {
            val runtime = runtime()
            claude.setMode("garbage")

            runtime.listSessions() shouldBe Outcome.Err(RuntimeError.Unparseable(UnparseableReason.MALFORMED))
        }

    @Test
    fun `hanging claude returns Timeout and leaves no process`() =
        runTest {
            val runtime = runtime(commandTimeout = 2.seconds)
            claude.setMode("hang")

            runtime.listSessions() shouldBe Outcome.Err(RuntimeError.Timeout)

            val pids = claude.pids("hang.pid")
            pids shouldHaveSize 2
            pids.forEach { isAlive(it, io) shouldBe false }
        }

    @Test
    fun `failing claude returns CommandFailed with a redacted stderr tail`() =
        runTest {
            val prompt = "secret plan for the merger"
            val runtime = runtime()
            claude.setMode("fail 3 Error: cannot start '$prompt' in /Users/jan/repo\nkey sk-ant-x rejected")

            val error = runtime.start(StartSpec(cwd = project, prompt = prompt)).errorOrNull()

            val failed = error.shouldBeInstanceOf<RuntimeError.CommandFailed>()
            assertSoftly(failed) {
                exitCode shouldBe 3
                stderrTail shouldNotContain prompt
                stderrTail shouldNotContain "/Users/jan/"
                stderrTail shouldNotContain "sk-ant-x"
                stderrTail shouldContain "~/repo"
                stderrTail shouldContain "***"
            }
            logger.entries.toString() shouldNotContain prompt
        }

    @Test
    fun `bg leaving a background child returns without waiting`() =
        // The child sleeps 30 s with stdout and stderr open. The process itself exits at once, so the
        // start timeout never fires; only the wall-clock limit of runTest catches waiting for the child.
        runTest(timeout = 10.seconds) {
            val runtime = runtime(startTimeout = 5.seconds)
            claude.setMode("bg-child")

            val started = runtime.start(StartSpec(cwd = project, prompt = "Fix it"))

            started.shouldBeInstanceOf<Outcome.Ok<*>>()
            claude.pids("bg-child.pid").shouldNotBeEmpty()
        }

    @Test
    fun `prompt with quotes, dollars and newlines reaches claude unchanged`() =
        runTest {
            val prompt = "-n \"double\" 'single' \$HOME \${PATH} `id` \$(whoami)\nline two\t* ? [a] \\ ; | &"
            val runtime = runtime()

            runtime.start(StartSpec(cwd = project, prompt = prompt)).getOrNull().shouldNotBeNull()

            claude.calls().single().last() shouldBe prompt
        }

    private companion object {
        /** What `claude` receives for the spec of `start passes the spec flags to claude`. */
        val EXPECTED_START_ARGS =
            listOf(
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
}
