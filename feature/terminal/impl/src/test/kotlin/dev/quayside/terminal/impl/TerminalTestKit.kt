package dev.quayside.terminal.impl

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Logger
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.swing.Swing
import java.awt.GraphicsEnvironment
import java.nio.file.Path

/**
 * Real dispatchers (Swing main) and `/bin/sh` with a minimal environment: the PTY tests wait for real processes.
 * [settings] is the live settings flow of the module (defaults, `optionAsMeta` off); a test changes `settings.value`
 * between two opens.
 */
internal class TerminalTestKit(
    home: Path,
    logger: Logger,
    extraEnvironment: Map<String, String> = emptyMap(),
    val settings: MutableStateFlow<TerminalSettings> = MutableStateFlow(TerminalSettings()),
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
            launch =
                TerminalLaunch(
                    shell = Path.of("/bin/sh"),
                    settings = settings,
                    baseEnvironment =
                        mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString()) + extraEnvironment,
                ),
        )

    override fun close() {
        api.close()
        scope.cancel()
    }

    companion object {
        val headless: Boolean get() = GraphicsEnvironment.isHeadless()
    }
}
