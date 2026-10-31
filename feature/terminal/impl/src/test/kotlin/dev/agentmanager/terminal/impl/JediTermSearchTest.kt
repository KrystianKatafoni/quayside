package dev.agentmanager.terminal.impl

import com.jediterm.terminal.ui.JediTermWidget
import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.terminal.api.SearchDirection
import dev.agentmanager.terminal.api.SearchMatch
import dev.agentmanager.terminal.api.SearchQuery
import dev.agentmanager.terminal.api.TerminalSession
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

/**
 * Highlights and scrolling of the real widget (plan AM-504, decisions 1 and 7); needs a display.
 * Waiting for output is real-time (see [awaitRow]).
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermSearchTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var kit: TerminalTestKit

    @BeforeEach
    fun setUp() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
        kit = TerminalTestKit(dir, NoOpLogger)
    }

    @AfterEach
    fun tearDown() {
        if (this::kit.isInitialized) kit.close()
    }

    private suspend fun sh(script: String): TerminalSession =
        kit.api
            .openCommand(ShellCommand(listOf("/bin/sh", "-c", script), dir))
            .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
            .value

    /**
     * A session that printed `line 1` .. `line 60`, more than the 24 screen rows, so most lines are history.
     * The last output is `end` without a newline: a pending newline after the awaited row would scroll the widget
     * back to the bottom after the test scrolled it (flaky `current hit is scrolled into view` on macOS CI).
     * The buffer gets the text before the panel's redraw timer widens the scroll range to the history, and until then
     * a scroll into the history is clamped to the screen, so the session also waits for the range ([awaitScrollRange]).
     */
    private suspend fun numbered(): TerminalSession =
        sh("i=1; while [ \$i -le 60 ]; do echo line \$i; i=\$((i+1)); done; printf end")
            .also {
                it.widget().awaitRow("end")
                it.widget().awaitScrollRange()
            }

    /** Waits (real time) until the vertical scroll range reaches the oldest history line. */
    @Suppress("InjectDispatcher") // Real time on purpose, like awaitRow: JediTerm updates the range on its own timer.
    private suspend fun JediTermWidget.awaitScrollRange() {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (onSwing { terminalPanel.verticalScrollModel.minimum != -terminalTextBuffer.historyLinesCount }) {
                    delay(20)
                }
            }
        }
    }

    /** Swing thread only (call it inside [onSwing]): `invokeAndWait` from the event thread throws. */
    private fun JediTermWidget.highlights(): SearchHighlights =
        terminalPanel as? SearchHighlights ?: error("the terminal panel does not draw search highlights")

    private fun <T> onSwing(block: () -> T): T {
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(block) }
        val finished = outcome ?: error("block did not run")
        return finished.getOrThrow()
    }

    /** Whether absolute [row] is among the rows the panel shows now. */
    private fun JediTermWidget.isVisible(row: Int): Boolean =
        onSwing {
            val buffer = terminalTextBuffer
            val origin = terminalPanel.verticalScrollModel.value
            val relative = row - buffer.historyLinesCount
            relative >= origin && relative < origin + buffer.height
        }

    @Test
    fun `find highlights all hits in the panel`() =
        runTest {
            val session = numbered()
            val widget = session.widget()
            val expectedRows =
                widget
                    .rows()
                    .withIndex()
                    .filter { "line 1" in it.value }
                    .map { it.index }

            val result = session.search.find(SearchQuery("line 1"))

            expectedRows.size shouldBe 11
            val hits = onSwing { widget.highlights().hits }
            hits.map { it.row } shouldContainExactly expectedRows
            hits.all { it.end - it.start == "line 1".length } shouldBe true
            hits.first().start shouldBe 0
            result.getOrNull()?.count shouldBe 11
        }

    @Test
    fun `current hit is scrolled into view`() =
        runTest {
            val session = numbered()
            val widget = session.widget()

            session.search.find(SearchQuery("line 1")) // newest hit: line 19, in the history
            val newest = onSwing { widget.highlights().selected }
            newest?.let { widget.isVisible(it.row) } shouldBe true

            repeat(times = 5) {
                session.search.step(SearchDirection.UP)
                val selected = onSwing { widget.highlights().selected }
                selected?.let { hit -> widget.isVisible(hit.row) } shouldBe true
            }
            session.search.step(SearchDirection.DOWN)
            val back = onSwing { widget.highlights().selected }
            back?.let { widget.isVisible(it.row) } shouldBe true
        }

    @Test
    fun `clear removes highlights`() =
        runTest {
            val session = numbered()
            val widget = session.widget()
            session.search.find(SearchQuery("line"))
            onSwing { widget.highlights().hits }.shouldNotBeEmpty()

            session.search.clear()

            onSwing { widget.highlights().hits } shouldBe emptyList()
            onSwing { widget.highlights().selected } shouldBe null
        }

    @Test
    fun `hits keep their rows when output grows`() =
        runTest {
            val session =
                sh(
                    "i=1; while [ \$i -le 30 ]; do echo early \$i; i=\$((i+1)); done; " +
                        "echo hit; echo last; read x; " +
                        "i=1; while [ \$i -le 40 ]; do echo filler \$i; i=\$((i+1)); done; sleep 30",
                )
            val widget = session.widget()
            widget.awaitRow("last")
            val historyBefore = onSwing { widget.terminalTextBuffer.historyLinesCount }
            historyBefore shouldBeGreaterThan 0
            session.search.find(SearchQuery("hit"))
            val before: List<SearchMatch> = onSwing { widget.highlights().hits }
            before.size shouldBe 1
            val hit = before.single()
            widget.rows()[hit.row] shouldBe "hit"

            session.send("go\r")
            widget.awaitRow("filler 40")

            onSwing { widget.terminalTextBuffer.historyLinesCount } shouldBeGreaterThan historyBefore
            onSwing { widget.highlights().hits } shouldBe before
            widget.rows()[hit.row] shouldBe "hit"
        }
}
