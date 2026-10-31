package dev.agentmanager.terminal.impl

import dev.agentmanager.core.domain.AppDispatchers
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.terminal.api.TerminalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import java.awt.GraphicsEnvironment
import java.nio.file.Path

/** Real dispatchers (Swing main) and `/bin/sh` with a minimal environment: the PTY tests run on real time. */
internal class TerminalTestKit(
    home: Path,
    logger: Logger,
    extraEnvironment: Map<String, String> = emptyMap(),
) : AutoCloseable {
    // Real time and real dispatchers on purpose: the PTY tests wait for real processes.
    @Suppress("InjectDispatcher")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Suppress("InjectDispatcher")
    val api: TerminalApi =
        terminalModule(
            dispatchers = AppDispatchers(main = Dispatchers.Swing, io = Dispatchers.IO, default = Dispatchers.Default),
            scope = scope,
            logger = logger,
            shell = Path.of("/bin/sh"),
            baseEnvironment = mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString()) + extraEnvironment,
        )

    override fun close() {
        api.close()
        scope.cancel()
    }

    companion object {
        val headless: Boolean get() = GraphicsEnvironment.isHeadless()
    }
}
