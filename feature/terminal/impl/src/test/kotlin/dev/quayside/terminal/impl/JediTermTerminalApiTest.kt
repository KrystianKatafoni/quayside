package dev.quayside.terminal.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.errorOrNull
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.TerminalError
import dev.quayside.terminal.api.TerminalSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Settings applied by the real `terminalModule` (pty4j + JediTerm, `/bin/sh`): the shell profile, the commands that
 * ignore it and the rule that a change reaches the next tab only (ADR 0016). Need a display.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermTerminalApiTest {
    @TempDir
    lateinit var dir: Path

    private val logger = RecordingLogger()
    private lateinit var kit: TerminalTestKit

    @BeforeEach
    fun setUp() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
        kit = TerminalTestKit(dir, logger)
    }

    @AfterEach
    fun tearDown() {
        if (this::kit.isInitialized) kit.close()
    }

    private fun fishInstalled(): Boolean =
        listOf("/opt/homebrew/bin/fish", "/usr/local/bin/fish").any { Files.exists(Path.of(it)) }

    @Test
    fun `missing shell of the profile fails with StartFailed`() =
        runTest {
            assumeFalse(fishInstalled(), "fish is installed on this machine")
            kit.settings.value = kit.settings.value.copy(shell = ShellProfile.FISH)

            val result = kit.api.openShell(dir)

            result.errorOrNull() shouldBe TerminalError.StartFailed
            logger.entriesAt(LogLevel.WARN).flatMap { it.fields.values } shouldContain "shellMissing"
        }

    @Test
    fun `closed module reports Closed before a missing shell`() =
        runTest {
            assumeFalse(fishInstalled(), "fish is installed on this machine")
            kit.settings.value = kit.settings.value.copy(shell = ShellProfile.FISH)
            kit.api.close()

            kit.api.openShell(dir).errorOrNull() shouldBe TerminalError.Closed
        }

    @Test
    fun `open command keeps the login shell for any profile`() =
        runTest {
            assumeFalse(fishInstalled(), "fish is installed on this machine")
            kit.settings.value = kit.settings.value.copy(shell = ShellProfile.FISH)

            val session =
                kit.api
                    .openCommand(ShellCommand(listOf("/bin/sh", "-c", "exit 3"), dir))
                    .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
                    .value

            session.awaitExit() shouldBe 3
        }

    @Test
    fun `settings changed after open apply to the next tab only`() =
        runTest {
            kit.settings.value = kit.settings.value.copy(scrollbackLines = SMALL)
            val first = kit.openShell()
            kit.settings.value = kit.settings.value.copy(scrollbackLines = LARGE)
            val second = kit.openShell()

            first.printLines()
            second.printLines()

            first.historyLines() shouldBeLessThanOrEqual SMALL
            second.historyLines() shouldBeGreaterThan SMALL
        }

    private suspend fun TerminalTestKit.openShell(): TerminalSession =
        api
            .openShell(dir)
            .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
            .value

    @Suppress("InjectDispatcher") // Real time and real dispatchers on purpose: the PTY tests wait for real processes.
    private suspend fun TerminalSession.awaitExit(): Int =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { exitCode.filterNotNull().first() } }

    private suspend fun TerminalSession.printLines() {
        send("seq 1 $PRINTED\r")
        widget().awaitRow(PRINTED.toString())
    }

    private fun TerminalSession.historyLines(): Int = widget().terminalTextBuffer.historyLinesCount

    private companion object {
        const val SMALL = 1_000
        const val LARGE = 50_000
        const val PRINTED = 3_000
    }
}
