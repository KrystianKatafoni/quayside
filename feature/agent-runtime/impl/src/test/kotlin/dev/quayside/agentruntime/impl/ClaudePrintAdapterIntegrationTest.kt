package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.LoginShellProcessRunner
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [ClaudePrintAdapter] through the real [LoginShellProcessRunner] on the fake `claude` (docs/12 §4): real
 * processes and real but short timeouts. The shared behaviour is in [ClaudePrintAdapterContractTest].
 */
class ClaudePrintAdapterIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private lateinit var claude: FakeClaude

    @AfterEach
    fun cleanUp() {
        if (::claude.isInitialized) killAll(claude.pids("hang.pid"))
        io.close()
    }

    private val home: Path get() = dir.resolve("home").createDirectories()

    private fun prompt(): OneShotPrompt {
        claude = FakeClaude.install(dir.resolve("claude"), RuntimeWorld())
        return oneShotPromptModule(LoginShellProcessRunner(io, TEST_SHELL), home, claude.executable)
    }

    private fun request(
        text: String = "Summarize this",
        timeout: Duration = 10.seconds,
    ) = PromptRequest(text, "haiku", timeout)

    @Test
    fun `prompt with quotes, dollars and newlines reaches claude unchanged`() =
        runTest {
            val text = "-p \"double\" 'single' \$HOME \${PATH} `id` \$(whoami)\nline two\t* ? [a] \\ ; | &"
            val port = prompt()

            val answer = port.ask(request(text))

            answer shouldBe Outcome.Ok("Fake answer.")
            claude.calls().single() shouldBe EXPECTED_FLAGS + text
        }

    @Test
    fun `claude runs in the home directory`() =
        runTest {
            prompt().ask(request())

            claude.printCwd() shouldBe home.toRealPath()
        }

    @Test
    fun `is_error answer gives CommandFailed without its text`() =
        runTest {
            val port = prompt()
            claude.setPrintResult("""{"type":"result","is_error":true,"result":"Credit balance is too low"}""")

            port.ask(request()) shouldBe Outcome.Err(RuntimeError.CommandFailed(0, ""))
        }

    @Test
    fun `garbage from claude gives Unparseable`() =
        runTest {
            val port = prompt()
            claude.setMode("garbage")

            port.ask(request()) shouldBe Outcome.Err(RuntimeError.Unparseable("print result"))
        }

    @Test
    fun `slow claude gives Timeout and leaves no process`() =
        runTest {
            val port = prompt()
            claude.setMode("hang")

            port.ask(request(timeout = 1.seconds)) shouldBe Outcome.Err(RuntimeError.Timeout)

            val pids = claude.pids("hang.pid")
            pids shouldHaveSize 2
            pids.forEach { isAlive(it, io) shouldBe false }
        }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = SEPARATE_THREAD)
    fun `cancel kills the fake claude`() =
        runTest {
            val port = prompt()
            claude.setMode("hang")
            val asking = launch(io) { port.ask(request(timeout = 60.seconds)) }
            withContext(io) {
                var checks = 0
                while (claude.pids("hang.pid").size < 2 && checks++ < MAX_CHECKS) delay(10.milliseconds)
            }
            val pids = claude.pids("hang.pid")
            pids shouldHaveSize 2

            asking.cancelAndJoin()

            pids.forEach { isAlive(it, io) shouldBe false }
        }

    private companion object {
        const val MAX_CHECKS = 1000

        /** What `claude` gets before the prompt (the arguments without the program). */
        val EXPECTED_FLAGS =
            listOf(
                "-p",
                "--model",
                "haiku",
                "--output-format",
                "json",
                "--settings",
                """{"disableAllHooks":true}""",
                "--no-session-persistence",
                "--tools",
                "",
                "--strict-mcp-config",
                "--disable-slash-commands",
                "--",
            )
    }
}
