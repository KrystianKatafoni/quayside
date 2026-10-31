package dev.agentmanager.terminal.impl

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.testing.LogEntry
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.terminal.api.TerminalError
import dev.agentmanager.terminal.api.TerminalSession
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests of sessions of the real `terminalModule` (pty4j + JediTerm, `/bin/sh`). Need a display.
 * Waiting is real-time inside `withContext(Dispatchers.Default)`: `runTest` would skip `withTimeout` instantly.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermSessionTest {
    @TempDir
    lateinit var dir: Path

    private val logger = RecordingLogger()
    private lateinit var kit: TerminalTestKit

    @BeforeEach
    fun setUp() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
        kit = TerminalTestKit(dir, logger, mapOf("AM_SECRET" to SECRET_ENV))
    }

    @AfterEach
    fun tearDown() {
        if (this::kit.isInitialized) kit.close()
    }

    private suspend fun command(vararg argv: String): TerminalSession =
        kit.api
            .openCommand(ShellCommand(argv.toList(), dir))
            .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
            .value

    @Suppress("InjectDispatcher") // Real time and real dispatchers on purpose: the PTY tests wait for real processes.
    private suspend fun TerminalSession.awaitExit(): Int =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { exitCode.filterNotNull().first() } }

    private fun descendants(): Set<Long> =
        ProcessHandle
            .current()
            .descendants()
            .map { it.pid() }
            .toList()
            .toSet()

    @Suppress("InjectDispatcher") // real time, see above
    private suspend fun awaitNewProcesses(
        before: Set<Long>,
        atLeast: Int,
    ): List<ProcessHandle> =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                var started: List<ProcessHandle>
                do {
                    started =
                        ProcessHandle
                            .current()
                            .descendants()
                            .filter { it.pid() !in before }
                            .toList()
                    if (started.size < atLeast) delay(20)
                } while (started.size < atLeast)
                started
            }
        }

    /** macOS `/bin/sh -l` also runs `path_helper`, so only the `sleep` processes of the tests are counted. */
    private fun newSleepProcesses(before: Set<Long>): List<ProcessHandle> =
        ProcessHandle
            .current()
            .descendants()
            .filter {
                it.pid() !in before &&
                    it
                        .info()
                        .command()
                        .orElse("")
                        .endsWith("sleep")
            }.toList()

    @Suppress("InjectDispatcher") // real time, see above
    private suspend fun awaitNoNewSleepProcesses(before: Set<Long>) =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (newSleepProcesses(before).any { it.isAlive }) delay(20)
            }
        }

    private fun List<ProcessHandle>.shouldAllExit() {
        forEach { it.onExit().get(5, TimeUnit.SECONDS) }
        all { !it.isAlive } shouldBe true
    }

    @Suppress("InjectDispatcher") // real time, see above
    private suspend fun awaitLog(message: String): LogEntry =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (logger.entries.none { it.message == message }) delay(20)
                logger.entries.first { it.message == message }
            }
        }

    @Test
    fun `exitCode is set when the command ends`() =
        runTest {
            val session = command("/bin/sh", "-c", "exit 5")

            session.awaitExit() shouldBe 5
        }

    @Test
    fun `send reaches the process`() =
        runTest {
            val session = command("/bin/sh", "-c", "read l; [ \"\$l\" = ping ] && exit 7")

            session.send("ping\r") shouldBe Outcome.Ok(Unit)

            session.awaitExit() shouldBe 7
        }

    @Test
    fun `send delivers UTF-8 text`() =
        runTest {
            val session = command("/bin/sh", "-c", "read l; [ \"\$l\" = 'zażółć' ] && exit 9")

            session.send("zażółć\r")

            session.awaitExit() shouldBe 9
        }

    @Test
    fun `send after the process exited returns Closed`() =
        runTest {
            val session = command("/bin/sh", "-c", "exit 0")
            session.awaitExit()

            session.send("x").errorOrNull() shouldBe TerminalError.Closed
        }

    @Test
    fun `an exited session stays open until closed`() =
        runTest {
            val session = command("/bin/sh", "-c", "exit 2")
            session.awaitExit()

            session.exitCode.value shouldBe 2
            session.close()
            session.close()
        }

    @Test
    fun `close kills the shell and its children`() =
        runTest {
            val before = descendants()
            val session = command("/bin/sh", "-c", "sleep 100 & wait")
            val started = awaitNewProcesses(before, atLeast = 1)

            session.close()

            started.shouldAllExit()
        }

    @Test
    fun `close of the api kills every session`() =
        runTest {
            val before = descendants()
            command("sleep", "100")
            command("sleep", "101")
            val started = awaitNewProcesses(before, atLeast = 2)

            kit.api.close()

            started.shouldAllExit()
        }

    @Test
    @Suppress("InjectDispatcher") // real dispatcher on purpose: the open must run while the test cancels it
    fun `open cancelled during the start leaves no process behind`() =
        runTest {
            val before = descendants()
            val job =
                launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    kit.api.openCommand(ShellCommand(listOf("sleep", "100"), dir))
                }

            job.cancel()
            job.join()

            awaitNoNewSleepProcesses(before)
        }

    @Test
    @Suppress("InjectDispatcher") // real dispatcher on purpose: close() races with the open
    fun `api close during open leaves a closed session or a refusal and no process`() =
        runTest {
            val before = descendants()
            val opening =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    kit.api.openCommand(ShellCommand(listOf("sleep", "100"), dir))
                }

            kit.api.close()
            val result = opening.await()

            if (result is Outcome.Ok) {
                result.value.send("x").errorOrNull() shouldBe TerminalError.Closed
            } else {
                result.errorOrNull() shouldBe TerminalError.Closed
            }
            awaitNoNewSleepProcesses(before)
        }

    @Test
    fun `logs hold ids and categories only`() =
        runTest {
            val session = command("/bin/sh", "-c", "exit 5 # $SECRET_ARG")
            session.awaitExit()
            awaitLog("terminal exited")
            kit.api.openShell(dir.resolve("does-not-exist"))

            val opened = logger.entries.first { it.message == "terminal opened" }
            opened.level shouldBe LogLevel.INFO
            opened.fields["terminalId"] shouldBe session.id.value
            opened.fields["kind"] shouldBe "command"
            val exited = logger.entries.first { it.message == "terminal exited" }
            exited.fields["terminalId"] shouldBe session.id.value
            exited.fields["exitCode"] shouldBe 5
            val failed = logger.entries.first { it.message == "terminal open failed" }
            failed.level shouldBe LogLevel.WARN
            failed.fields["reason"] shouldBe "workDirMissing"

            logger.entries.shouldNotBeEmpty()
            logger.entries.forEach { entry ->
                val text = "${entry.message} ${entry.fields}"
                text shouldNotContain SECRET_ARG
                text shouldNotContain SECRET_ENV
                if (entry.level != LogLevel.DEBUG) text shouldNotContain dir.toString()
            }
        }

    private companion object {
        const val SECRET_ARG = "SECRET_ARG_VALUE"
        const val SECRET_ENV = "SECRET_ENV_VALUE"
    }
}
