package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.AgentRuntimeContract
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.core.testing.TestClock
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories

/**
 * [AgentRuntimeContract] on the real adapter, the real [LoginShellProcessRunner] and the fake
 * `claude` from [FakeClaude] (docs/12 §3). Health: [ClaudeCliAdapterHealthContractTest].
 */
class ClaudeCliAdapterContractTest : AgentRuntimeContract() {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private var installs = 0

    @AfterEach
    fun closeDispatcher() = io.close()

    // Physical path: `pwd -P` in the fake resolves links such as /var -> /private/var on macOS.
    override val startCwd: Path
        get() = dir.resolve("project").createDirectories().toRealPath()

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
