package dev.quayside.terminal.impl

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JediTerm `TtyConnector` over a pty4j [PtyProcess]. Reading and writing go through the PTY streams of
 * [ProcessTtyConnector]; `resize` sets the PTY window size and `close` kills the whole process tree (idempotent).
 */
internal class PtyTtyConnector(
    private val ptyProcess: PtyProcess,
    charset: Charset = Charsets.UTF_8,
) : ProcessTtyConnector(ptyProcess, charset) {
    private val closed = AtomicBoolean(false)

    override fun getName(): String = "pty4j"

    override fun resize(termSize: TermSize) {
        if (ptyProcess.isAlive) {
            ptyProcess.winSize = WinSize(termSize.columns, termSize.rows)
        }
    }

    /** Kills the descendants first, so a shell that dies early cannot leave orphans behind. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // pty4j does not implement Process.toHandle(), but pid() works.
        ProcessHandle.of(ptyProcess.pid()).ifPresent { handle ->
            handle.descendants().forEach { it.destroyForcibly() }
        }
        ptyProcess.destroyForcibly()
        super.close()
    }
}
