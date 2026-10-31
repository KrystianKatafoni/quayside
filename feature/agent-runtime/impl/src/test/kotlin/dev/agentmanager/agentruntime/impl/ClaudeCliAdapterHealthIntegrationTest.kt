package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimeHealth
import dev.agentmanager.agentruntime.api.RuntimeRequirements.MIN_CLAUDE_VERSION
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.TestClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
 * [ClaudeCliAdapter.health] through the real [LoginShellProcessRunner] on the fake `claude`
 * (docs/12 §4, plan AM-204): real processes, real but short timeouts, state in a temp dir.
 */
class ClaudeCliAdapterHealthIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private lateinit var claude: FakeClaude

    @AfterEach
    fun cleanUp() {
        if (::claude.isInitialized) killAll(claude.pids("hang.pid"))
        io.close()
    }

    private fun runtime(
        world: RuntimeWorld = RuntimeWorld(version = "2.1.302"),
        commandTimeout: Duration = 10.seconds,
    ): AgentRuntime {
        claude = FakeClaude.install(dir.resolve("claude"), world)
        return agentRuntimeModule(
            process = LoginShellProcessRunner(io, TEST_SHELL),
            clock = TestClock(),
            logger = logger,
            homeDir = dir.resolve("home").createDirectories(),
            executable = claude.executable,
            commandTimeout = commandTimeout,
        )
    }

    @Test
    fun `ok installation is ready with a running supervisor`() =
        runTest {
            val runtime = runtime()

            val health = runtime.health()

            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = true, emptyList())
            claude.calls() shouldBe HEALTH_CALLS
        }

    @Test
    fun `old version is unsupported and daemon is never called`() =
        runTest {
            val runtime = runtime(RuntimeWorld(version = "2.1.100"))

            val health = runtime.health()

            health shouldBe
                RuntimeHealth(
                    version = "2.1.100",
                    agentViewEnabled = false,
                    supervisorRunning = false,
                    problems = listOf(RuntimeError.UnsupportedVersion("2.1.100", MIN_CLAUDE_VERSION)),
                )
            claude.calls() shouldBe listOf(listOf("--version"))
        }

    @Test
    fun `missing binary is NotInstalled`() =
        runTest {
            val runtime = runtime(RuntimeWorld(installed = false))

            val health = runtime.health()

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.NotInstalled))
            claude.calls() shouldBe emptyList()
        }

    @Test
    fun `Agent View disabled by env and by setting`() =
        runTest {
            val runtime = runtime(RuntimeWorld(version = "2.1.302", agentViewEnabled = false))

            val byEnv = runtime.health()
            claude.setAgentView(FakeClaude.AgentView.SETTING)
            val bySetting = runtime.health()

            val disabled =
                RuntimeHealth(
                    version = "2.1.302",
                    agentViewEnabled = false,
                    supervisorRunning = true,
                    problems = listOf(RuntimeError.AgentViewDisabled),
                )
            assertSoftly {
                byEnv shouldBe disabled
                bySetting shouldBe disabled
            }
        }

    @Test
    fun `stopped daemon is ready but not running`() =
        runTest {
            val runtime = runtime()
            claude.setDaemon(FakeClaude.Daemon.STOPPED)

            val health = runtime.health()

            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = false, emptyList())
            health.isReady shouldBe true
            claude.calls() shouldBe HEALTH_CALLS
        }

    @Test
    fun `absent daemon command is ready but not running`() =
        runTest {
            val runtime = runtime()
            claude.setDaemon(FakeClaude.Daemon.ABSENT)

            val health = runtime.health()

            health shouldBe RuntimeHealth("2.1.302", agentViewEnabled = true, supervisorRunning = false, emptyList())
            claude.calls() shouldBe HEALTH_CALLS
        }

    @Test
    fun `garbage version is Unparseable`() =
        runTest {
            val runtime = runtime()
            claude.setMode("garbage")

            val health = runtime.health()

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.Unparseable("version:unparseable")))
            claude.calls() shouldBe listOf(listOf("--version"))
        }

    @Test
    fun `hanging version returns Timeout and leaves no process`() =
        runTest {
            val runtime = runtime(commandTimeout = 2.seconds)
            claude.setMode("hang")

            val health = runtime.health()

            health shouldBe RuntimeHealth(null, false, false, listOf(RuntimeError.Timeout))
            claude.calls() shouldBe listOf(listOf("--version"))
            val pids = claude.pids("hang.pid")
            pids shouldHaveSize 2
            pids.forEach { isAlive(it, io) shouldBe false }
        }

    private companion object {
        /** What `claude` receives for one full health check, in order. */
        val HEALTH_CALLS = listOf(listOf("--version"), listOf("daemon", "status"), listOf("agents", "--json"))
    }
}
