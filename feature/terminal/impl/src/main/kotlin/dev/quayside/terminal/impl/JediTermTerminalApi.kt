package dev.quayside.terminal.impl

import com.pty4j.PtyProcess
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalError
import dev.quayside.terminal.api.TerminalId
import dev.quayside.terminal.api.TerminalSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/** What to start: [argv] in [workDir] with [extraEnv]; [kind] (`shell` or `command`) is logged. */
private class Launch(
    val kind: String,
    val argv: List<String>,
    val workDir: Path,
    val extraEnv: Map<String, String> = emptyMap(),
)

/** [TerminalApi] over pty4j and JediTerm; see [terminalModule]. */
internal class JediTermTerminalApi(
    private val context: SessionContext,
    private val shell: Path,
    private val baseEnvironment: Map<String, String>,
) : TerminalApi {
    private val sessions = ConcurrentHashMap<TerminalId, JediTermSession>()
    private val closed = AtomicBoolean(false)
    private val counter = AtomicLong()

    override suspend fun openShell(dir: Path): Outcome<TerminalSession, TerminalError> =
        open(Launch("shell", ShellLaunch.shell(shell), dir))

    override suspend fun openCommand(command: ShellCommand): Outcome<TerminalSession, TerminalError> =
        open(Launch("command", ShellLaunch.command(shell, command.argv), command.workDir, command.env))

    override fun close() {
        closed.set(true)
        sessions.values.forEach { it.close() }
    }

    private suspend fun open(launch: Launch): Outcome<TerminalSession, TerminalError> {
        if (closed.get()) return Outcome.Err(TerminalError.Closed)
        // Not cancellable: a cancelled caller must not lose a started process or a built widget (no orphans).
        val result =
            withContext(NonCancellable) {
                withContext(context.dispatchers.io) { startProcess(launch) }
                    .flatMap { process -> withContext(context.dispatchers.main) { buildSession(process) } }
            }
        return when (result) {
            is Outcome.Err -> result.also { context.logger.warn("terminal open failed", "reason" to reason(it.error)) }
            is Outcome.Ok -> register(result.value, launch)
        }
    }

    private fun startProcess(launch: Launch): Outcome<PtyProcess, TerminalError> =
        if (!Files.isDirectory(launch.workDir)) {
            Outcome.Err(TerminalError.WorkDirMissing)
        } else {
            val env = ShellLaunch.environment(baseEnvironment, launch.extraEnv)
            PtyLauncher.start(launch.argv, launch.workDir, env)
        }

    /** Swing thread. A widget that fails to build takes the process down with it. */
    private fun buildSession(process: PtyProcess): Outcome<JediTermSession, TerminalError> =
        catching(onException = {
            process.destroyForcibly()
            TerminalError.StartFailed
        }) {
            val connector = PtyTtyConnector(process)
            val widget = SearchableWidget(TerminalWidgetSettings())
            widget.setTtyConnector(connector)
            widget.start()
            val id = TerminalId("t-${counter.incrementAndGet()}")
            JediTermSession(id, connector, widget, context) { sessions.remove(it) }
        }

    private suspend fun register(
        session: JediTermSession,
        launch: Launch,
    ): Outcome<TerminalSession, TerminalError> {
        sessions[session.id] = session
        if (closed.get()) {
            session.close()
            return Outcome.Err(TerminalError.Closed)
        }
        if (!currentCoroutineContext().isActive) {
            session.close()
            throw CancellationException("terminal open cancelled")
        }
        context.logger.info("terminal opened", "terminalId" to session.id.value, "kind" to launch.kind)
        context.logger.debug(
            "terminal program",
            "program" to launch.argv.first(),
            "workDir" to launch.workDir.toString(),
        )
        return Outcome.Ok(session)
    }

    private fun reason(error: TerminalError): String =
        when (error) {
            TerminalError.WorkDirMissing -> "workDirMissing"
            TerminalError.StartFailed -> "startFailed"
            TerminalError.Closed -> "closed"
        }
}
