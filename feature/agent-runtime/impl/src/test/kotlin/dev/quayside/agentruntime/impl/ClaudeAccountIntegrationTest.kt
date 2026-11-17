package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.ResumeSpec
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.StartSpec
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.seconds

/**
 * `CLAUDE_CONFIG_DIR` of an extra account on every `claude` call, through the real [LoginShellProcessRunner] and the
 * fake `claude`, which records the variable of each call in `config-dir.log` (plan AM-1003, docs/compat.md).
 */
class ClaudeAccountIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private lateinit var claude: FakeClaude

    @AfterEach
    fun cleanUp() = io.close()

    private val work = AccountId("work")
    private val project: Path get() = dir.resolve("project").createDirectories().toRealPath()
    private val workDir: Path get() = dir.resolve("config").resolve("work")
    private val known: RuntimeSession get() = aRuntimeSession(id = SessionId("7c5dcf5d"), cwd = project)

    private fun runtime(
        account: AccountId,
        configDir: Path?,
    ): AgentRuntime {
        claude = FakeClaude.install(dir.resolve("claude"), RuntimeWorld(sessions = listOf(known)))
        return agentRuntimeModule(
            process = LoginShellProcessRunner(io, TEST_SHELL),
            clock = TestClock(),
            logger = logger,
            homeDir = dir.resolve("home").createDirectories(),
            executable = claude.executable,
            account = account,
            configDir = configDir,
        )
    }

    /** Every operation once; [CALLS] is the first argument of each resulting `claude` call, in order. */
    private suspend fun AgentRuntime.useEverything() {
        listSessions()
        start(StartSpec(cwd = project, prompt = "Fix it"))
        resume(ResumeSpec(ConversationId("5e2a9c41-0000-4000-8000-0000000000aa"), project))
        stop(known.id)
        respawn(known.id)
        recentOutput(known.id)
        remove(known.id)
        health()
    }

    @Test
    fun `every command of an extra account carries CLAUDE_CONFIG_DIR`() =
        runTest {
            val runtime = runtime(work, workDir)

            runtime.useEverything()

            claude.calls().map { it.first() } shouldBe CALLS
            claude.configDirs() shouldBe List(CALLS.size) { workDir.toString() }
        }

    @Test
    fun `the attach command of an extra account carries CLAUDE_CONFIG_DIR`() {
        val runtime = runtime(work, workDir)

        runtime.attachCommand(known.id).env shouldBe mapOf("CLAUDE_CONFIG_DIR" to workDir.toString())
    }

    @Test
    fun `no command of the default account carries CLAUDE_CONFIG_DIR even with configDir`() =
        runTest {
            val runtime = runtime(AccountId.DEFAULT, dir.resolve("config").resolve(".claude"))

            runtime.useEverything()

            claude.calls().map { it.first() } shouldBe CALLS
            claude.configDirs() shouldBe List(CALLS.size) { "-" }
            runtime.attachCommand(known.id).env shouldBe emptyMap()
        }

    @Test
    fun `a config dir with spaces is passed verbatim`() =
        runTest {
            val spaced = dir.resolve("My Dir").resolve("it's \"work\"")
            val runtime = runtime(work, spaced)

            runtime.listSessions()
            runtime.stop(known.id)

            claude.configDirs() shouldBe listOf(spaced.toString(), spaced.toString())
        }

    @Test
    fun `oneShot prompt of an extra account carries CLAUDE_CONFIG_DIR`() =
        runTest {
            claude = FakeClaude.install(dir.resolve("claude"), RuntimeWorld())
            val prompt =
                oneShotPromptModule(
                    LoginShellProcessRunner(io, TEST_SHELL),
                    dir.resolve("home").createDirectories(),
                    claude.executable,
                    work,
                    workDir,
                )

            prompt.ask(PromptRequest("Summarize this", "haiku", 10.seconds)) shouldBe Outcome.Ok("Fake answer.")

            claude.configDirs() shouldBe listOf(workDir.toString())
        }

    @Test
    fun `oneShot prompt of the default account has no CLAUDE_CONFIG_DIR`() =
        runTest {
            claude = FakeClaude.install(dir.resolve("claude"), RuntimeWorld())
            val prompt =
                oneShotPromptModule(
                    LoginShellProcessRunner(io, TEST_SHELL),
                    dir.resolve("home").createDirectories(),
                    claude.executable,
                    AccountId.DEFAULT,
                    workDir,
                )

            prompt.ask(PromptRequest("Summarize this", "haiku", 10.seconds))

            claude.configDirs() shouldBe listOf("-")
        }

    @Test
    fun `the config dir never reaches the log or the stderr tail`() =
        runTest {
            val runtime = runtime(work, workDir)
            runtime.useEverything()
            claude.setMode("fail 3 Error: not logged in")

            val errors =
                listOf(
                    runtime.listSessions(),
                    runtime.start(StartSpec(cwd = project, prompt = "Fix it")),
                    runtime.stop(known.id),
                    runtime.recentOutput(known.id),
                )
            val health = runtime.health()

            errors.forEach { it.toString() shouldNotContain workDir.toString() }
            health.toString() shouldNotContain workDir.toString()
            logger.entries.toString() shouldNotContain workDir.toString()
            claude.configDirs().distinct() shouldBe listOf(workDir.toString())
        }

    private companion object {
        /** First argument of each call of `useEverything`: agents, start, resume, stop, respawn, logs, rm, health. */
        val CALLS =
            listOf("agents", "--bg", "--bg", "stop", "respawn", "logs", "rm", "--version", "daemon", "agents")
    }
}
