package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow
import javax.swing.JComponent

/**
 * One open terminal: a process in a PTY and its widget. The process lives until it exits or [close];
 * an exited session keeps its screen until [close].
 */
public interface TerminalSession : AutoCloseable {
    public val id: TerminalId

    /** Null while the process runs; its exit code afterwards. Updated from a background thread. */
    public val exitCode: StateFlow<Int?>

    /** The same widget on every call, embedded by `terminal:ui` via `SwingPanel`. Swing event thread only. */
    public fun component(): JComponent

    /** Search in this terminal's scrollback and screen (FR-T3); works after the process exited, until [close]. */
    public val search: TerminalSearch

    /**
     * Types [text] into the PTY as if from the keyboard (UTF-8, no newline added; `"\r"` is Enter).
     * Any dispatcher; does not block the caller. Err([TerminalError.Closed]) after [close] or process exit.
     */
    public suspend fun send(text: String): Outcome<Unit, TerminalError>

    /** Kills the process and all its descendants, then disposes the widget. Any thread, idempotent. */
    override fun close()
}
