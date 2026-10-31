package dev.quayside.terminal.impl.replyspike

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import dev.quayside.terminal.impl.PtyTtyConnector
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val POLL = 20.milliseconds

/** Placeholder of an unwritten cell and the marker behind a double-width character. */
private val FILLERS = setOf('\u0000', '')

/**
 * jediterm-core emulator (no Swing) over a PTY; rows are read under the buffer lock. The reader thread ends with
 * the PTY (end of stream); [close] only stops this screen from being fed, the connector is closed by its owner.
 */
internal class HeadlessScreen(
    connector: PtyTtyConnector,
    columns: Int = 120,
    rows: Int = 40,
) : AutoCloseable {
    @Volatile
    private var closed = false
    private val buffer: TerminalTextBuffer

    init {
        val style = StyleState()
        buffer = TerminalTextBuffer(columns, rows, style)
        val emulator = JediEmulator(TtyBasedArrayDataStream(connector), JediTerminal(NoDisplay, buffer, style))
        thread(name = "headless-screen", isDaemon = true) { pump(emulator) }
    }

    private fun pump(emulator: JediEmulator) {
        try {
            while (!closed && emulator.hasNext()) emulator.next()
        } catch (ignored: IOException) {
            // The PTY ended: the screen keeps its last content.
        }
    }

    /** Text of every screen row (alternate buffer when shown); unwritten cells are blanks. */
    fun rows(): List<String> {
        buffer.lock()
        try {
            return List(buffer.height) { row -> blanked(buffer.getLine(row).text) }
        } finally {
            buffer.unlock()
        }
    }

    /** Real time; true when [predicate] held before [timeout]. */
    suspend fun await(
        timeout: Duration,
        predicate: (List<String>) -> Boolean,
    ): Boolean {
        val start = TimeSource.Monotonic.markNow()
        while (!predicate(rows())) {
            if (start.elapsedNow() >= timeout) return false
            delay(POLL)
        }
        return true
    }

    override fun close() {
        closed = true
    }
}

private fun blanked(text: String): String = text.map { if (it in FILLERS) ' ' else it }.joinToString("")

/** Display callbacks of the emulator; nothing is drawn. One function per callback of the interface. */
@Suppress("TooManyFunctions")
private object NoDisplay : TerminalDisplay {
    private var title = ""

    override fun setCursor(
        x: Int,
        y: Int,
    ) = Unit

    override fun setCursorShape(cursorShape: CursorShape?) = Unit

    override fun beep() = Unit

    override fun scrollArea(
        scrollRegionTop: Int,
        scrollRegionSize: Int,
        dy: Int,
    ) = Unit

    override fun setCursorVisible(isCursorVisible: Boolean) = Unit

    override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) = Unit

    override fun getWindowTitle(): String = title

    override fun setWindowTitle(windowTitle: String) {
        title = windowTitle
    }

    override fun getSelection(): TerminalSelection? = null

    override fun terminalMouseModeSet(mouseMode: MouseMode) = Unit

    override fun setMouseFormat(mouseFormat: MouseFormat) = Unit

    override fun ambiguousCharsAreDoubleWidth(): Boolean = false
}
