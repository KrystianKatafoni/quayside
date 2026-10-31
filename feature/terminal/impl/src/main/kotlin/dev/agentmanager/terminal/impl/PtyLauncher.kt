package dev.agentmanager.terminal.impl

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.catching
import dev.agentmanager.terminal.api.TerminalError
import java.nio.file.Path

private const val INITIAL_COLUMNS = 80
private const val INITIAL_ROWS = 24

/** Starts a pty4j process (80x24); blocking, call on IO. Never carries exception text (ENG-C8). */
internal object PtyLauncher {
    /**
     * Err([TerminalError.StartFailed]) when pty4j cannot start [argv] (missing binary, native library).
     * [launch] is the injection point for tests; it receives the configured builder.
     */
    fun start(
        argv: List<String>,
        workDir: Path,
        environment: Map<String, String>,
        launch: (PtyProcessBuilder) -> PtyProcess = PtyProcessBuilder::start,
    ): Outcome<PtyProcess, TerminalError> =
        try {
            catching(onException = { TerminalError.StartFailed }) {
                launch(
                    PtyProcessBuilder(argv.toTypedArray())
                        .setEnvironment(environment)
                        .setDirectory(workDir.toString())
                        .setInitialColumns(INITIAL_COLUMNS)
                        .setInitialRows(INITIAL_ROWS),
                )
            }
        } catch (_: LinkageError) {
            Outcome.Err(TerminalError.StartFailed)
        }
}
