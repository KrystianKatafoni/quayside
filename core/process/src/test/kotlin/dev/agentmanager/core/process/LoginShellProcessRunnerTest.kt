package dev.agentmanager.core.process

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.testing.RecordingLogger
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests on real local processes through `/bin/sh -l` (docs/12 §4). Only POSIX tools
 * are used; `HOME` points to the temp dir; timeouts are real but short.
 */
class LoginShellProcessRunnerTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private val runner = LoginShellProcessRunner(io, Path.of("/bin/sh"), logger)

    @AfterEach
    fun closeDispatcher() = io.close()

    private fun cmd(
        vararg argv: String,
        env: Map<String, String> = emptyMap(),
    ) = ShellCommand(argv.toList(), dir, hermeticHome + env)

    // The login shell must never read the real profile of the developer or the CI runner.
    private val hermeticHome get() = mapOf("HOME" to dir.toString(), "ZDOTDIR" to dir.toString())

    @Test
    fun `returns stdout, stderr and exit code`() =
        runTest {
            val result = runner.run(cmd("sh", "-c", "printf out; printf err >&2; exit 3")).getOrNull()

            result shouldBe ProcessResult(exitCode = 3, stdout = "out", stderr = "err")
            result.shouldNotBeNull().isSuccess shouldBe false
        }

    @Test
    fun `passes arguments without shell interpretation`() =
        runTest {
            val result = runner.run(cmd("printf", "%s|", "a b", "\$HOME", "*", "'q'")).getOrNull()

            result.shouldNotBeNull().stdout shouldBe "a b|\$HOME|*|'q'|"
        }

    @Test
    fun `runs in the working directory with the extra environment`() =
        runTest {
            val command = cmd("sh", "-c", "pwd -P; printf %s \"\$AM_TEST\"", env = mapOf("AM_TEST" to "x y"))

            runner
                .run(command)
                .getOrNull()
                .shouldNotBeNull()
                .stdout shouldBe "${dir.toRealPath()}\nx y"
        }

    @Test
    fun `loads the login profile`() =
        runTest {
            dir.resolve(".profile").writeText("export AM_PROFILE=loaded\n")

            val command = cmd("sh", "-c", "printf %s \"\$AM_PROFILE\"")

            runner
                .run(command)
                .getOrNull()
                .shouldNotBeNull()
                .stdout shouldBe "loaded"
        }

    @Test
    fun `loads zprofile when the shell is zsh`() =
        runTest {
            val zsh = Path.of("/bin/zsh")
            assumeTrue(zsh.exists(), "zsh is not installed")
            dir.resolve(".zprofile").writeText("export AM_PROFILE=zsh\n")
            val zshRunner = LoginShellProcessRunner(io, zsh)

            val command = cmd("sh", "-c", "printf %s \"\$AM_PROFILE\"")

            zshRunner
                .run(command)
                .getOrNull()
                .shouldNotBeNull()
                .stdout shouldBe "zsh"
        }

    @Test
    fun `keeps only the start of large output and still lets the process finish`() =
        runTest {
            val big = "head -c 200000 /dev/zero | tr '\\000' a; printf done >&2"

            val outcome = runner.run(cmd("sh", "-c", big), RunOptions(maxOutputBytes = 1000))

            val result = outcome.getOrNull().shouldNotBeNull()

            result.exitCode shouldBe 0
            result.stdout shouldBe "a".repeat(1000)
            result.stdoutTruncated shouldBe true
            result.stderr shouldBe "done"
            result.stderrTruncated shouldBe false
        }

    @Test
    fun `kills the process tree after the timeout`() =
        runTest {
            val pidFile = dir.resolve("child.pid")

            val command = cmd("sh", "-c", "sleep 30 & echo \$! > child.pid; wait")

            val outcome = runner.run(command, RunOptions(timeout = 2.seconds))

            outcome shouldBe Outcome.Err(ProcessError.Timeout(2.seconds))
            isAlive(pidFile) shouldBe false
            logger.entriesAt(LogLevel.WARN).map { it.message } shouldBe listOf("process timeout")
        }

    @Test
    fun `returns when the process exits although a background child keeps its output open`() =
        // Without a bounded drain the readers wait for EOF, i.e. for the 30 s child; the wall-clock
        // limit of runTest catches that, the run timeout cannot (the process itself exits at once).
        runTest(timeout = 10.seconds) {
            val pidFile = dir.resolve("child.pid")
            val command = cmd("sh", "-c", "sleep 30 & echo \$! > child.pid; printf started; printf warn >&2")

            try {
                val result = runner.run(command, RunOptions(timeout = 5.seconds)).getOrNull()

                result shouldBe ProcessResult(exitCode = 0, stdout = "started", stderr = "warn")
            } finally {
                killFrom(pidFile)
            }
        }

    @Test
    fun `kills the process when the caller is cancelled`() =
        runTest {
            val pidFile = dir.resolve("self.pid")
            val running = async { runner.run(cmd("sh", "-c", "echo \$\$ > self.pid; exec sleep 30")) }
            withContext(io) {
                while (!pidFile.exists() || pidFile.readText().isBlank()) delay(10.milliseconds)
            }

            running.cancelAndJoin()

            isAlive(pidFile) shouldBe false
        }

    @Test
    fun `reports a program missing from PATH as not found`() =
        runTest {
            val outcome = runner.run(cmd("am-no-such-program", "--version"))

            outcome shouldBe Outcome.Err(ProcessError.NotFound("am-no-such-program"))
        }

    @Test
    fun `reports a missing working directory as start failure`() =
        runTest {
            val outcome = runner.run(ShellCommand(listOf("true"), dir.resolve("missing")))

            outcome.errorOrNull().shouldBeInstanceOf<ProcessError.StartFailed>()
        }

    @Test
    fun `never logs environment values`() =
        runTest {
            runner.run(cmd("true", env = mapOf("TOKEN" to "secret-value")))

            logger.entries.map { it.message } shouldBe listOf("process start")
            logger.entries.toString().contains("secret-value") shouldBe false
        }

    /** Kills a background child the test left behind on purpose, with its own children. */
    private fun killFrom(pidFile: Path) {
        if (!pidFile.exists()) return
        val pid = pidFile.readText().trim().toLongOrNull() ?: return
        ProcessHandle.of(pid).ifPresent { child ->
            child.descendants().forEach { it.destroyForcibly() }
            child.destroyForcibly()
        }
    }

    /** Polls briefly: a killed orphan is reaped by init shortly after the signal. */
    private suspend fun isAlive(pidFile: Path): Boolean =
        withContext(io) {
            val handle = ProcessHandle.of(pidFile.readText().trim().toLong())
            var checks = 0
            while (handle.map { it.isAlive }.orElse(false) && checks++ < MAX_CHECKS) delay(10.milliseconds)
            handle.map { it.isAlive }.orElse(false)
        }

    @Test
    fun `default shell is an absolute path`() {
        LoginShellProcessRunner.defaultShell().isAbsolute shouldBe true
    }

    private companion object {
        const val MAX_CHECKS = 300
    }
}
