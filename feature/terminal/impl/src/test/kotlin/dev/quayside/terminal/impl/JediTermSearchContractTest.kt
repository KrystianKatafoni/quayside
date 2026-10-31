package dev.quayside.terminal.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.terminal.api.TerminalSearchContract
import dev.quayside.terminal.api.TerminalSession
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** The real `terminalModule` (pty4j + JediTerm, `/bin/sh`) passes [TerminalSearchContract]; needs a display. */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermSearchContractTest : TerminalSearchContract() {
    @TempDir
    lateinit var dir: Path

    private var kit: TerminalTestKit? = null

    @BeforeEach
    fun requireDisplay() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
    }

    override suspend fun sessionShowing(lines: List<String>): TerminalSession {
        val api = (kit ?: TerminalTestKit(dir, NoOpLogger).also { kit = it }).api
        val session =
            api
                .openCommand(ShellCommand(listOf("printf", "%s\\n") + lines, dir))
                .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
                .value
        session.widget().awaitRow(lines.last())
        return session
    }

    @Suppress("InjectDispatcher") // Real time on purpose: waits for a real process.
    override suspend fun endProcess(session: TerminalSession) {
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { session.exitCode.filterNotNull().first() } }
    }

    override fun tearDown() {
        kit?.close()
    }
}
