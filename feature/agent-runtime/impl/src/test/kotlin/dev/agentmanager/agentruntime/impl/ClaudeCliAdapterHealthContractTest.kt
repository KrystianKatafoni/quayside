package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.AgentRuntimeHealthContract
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.core.testing.TestClock
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories

/**
 * [AgentRuntimeHealthContract] on the real adapter, the real [LoginShellProcessRunner] and the fake
 * `claude` from [FakeClaude], set up like [ClaudeCliAdapterContractTest] (plan AM-204).
 */
class ClaudeCliAdapterHealthContractTest : AgentRuntimeHealthContract() {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private var installs = 0

    @AfterEach
    fun closeDispatcher() = io.close()

    override fun create(world: RuntimeWorld): AgentRuntime {
        val claude = FakeClaude.install(dir.resolve("claude-${++installs}"), world)
        return agentRuntimeModule(
            process = LoginShellProcessRunner(io, TEST_SHELL),
            clock = TestClock(),
            logger = NoOpLogger,
            homeDir = dir.resolve("home").createDirectories(),
            executable = claude.executable,
        )
    }
}
