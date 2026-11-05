package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors
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
 * [settings] and [colors] are the live flows of the module (defaults, `optionAsMeta` off); a test changes `.value`
 * between two opens.
 */
internal class TerminalTestKit(
    home: Path,
    logger: Logger,
    extraEnvironment: Map<String, String> = emptyMap(),
    val settings: MutableStateFlow<TerminalSettings> = MutableStateFlow(TerminalSettings()),
    val colors: MutableStateFlow<TerminalColors> = MutableStateFlow(DEFAULT_COLORS),
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
                    colors = colors,
                    baseEnvironment =
                        mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString()) + extraEnvironment,
                ),
        )

    override fun close() {
        api.close()
        scope.cancel()
    }

    companion object {
        /** Colours that differ from the JediTerm defaults (black on white), so a test can see them apply. */
        val DEFAULT_COLORS = TerminalColors(termBg = Rgba(0xFF0B0A10), termText = Rgba(0xFFE8E5F2))

        val headless: Boolean get() = GraphicsEnvironment.isHeadless()
    }
}
