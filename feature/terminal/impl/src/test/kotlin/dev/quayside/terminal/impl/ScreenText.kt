package dev.quayside.terminal.impl

import com.jediterm.terminal.ui.JediTermWidget
import dev.quayside.terminal.api.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

/** The JediTerm widget behind [this] session (the component is read on the Swing thread). */
internal fun TerminalSession.widget(): JediTermWidget {
    var widget: JediTermWidget? = null
    SwingUtilities.invokeAndWait { widget = component() as? JediTermWidget }
    return widget ?: error("session component is not a JediTermWidget")
}

/** Text of every row from the oldest scrollback line to the last screen row; empty cells are blanks, tails trimmed. */
internal fun JediTermWidget.rows(): List<String> {
    val buffer = terminalTextBuffer
    buffer.lock()
    try {
        return (-buffer.historyLinesCount until buffer.height).map {
            buffer
                .getLine(it)
                .text
                .replace('\u0000', ' ')
                .trimEnd()
        }
    } finally {
        buffer.unlock()
    }
}

/** Waits (real time) until a row equals [text]; the PTY output arrives on another thread. */
@Suppress("InjectDispatcher") // Real time and real dispatchers on purpose: the PTY tests wait for real processes.
internal suspend fun JediTermWidget.awaitRow(text: String) {
    withContext(Dispatchers.Default) {
        withTimeout(5.seconds) {
            while (text !in rows()) delay(20)
        }
    }
}
