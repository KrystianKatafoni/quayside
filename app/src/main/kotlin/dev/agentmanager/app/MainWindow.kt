package dev.agentmanager.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.FrameWindowScope
import dev.agentmanager.commandpalette.api.CommandProvider
import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.terminal.ui.TerminalArea

/** The content and menu of the main window; its controllers live as long as the window (AM-408). */
@Composable
internal fun FrameWindowScope.MainWindow(
    core: CoreDeps,
    modules: AppModules,
    keys: WindowKeys,
) {
    val scope = rememberCoroutineScope()
    val controllers =
        remember { WindowControllers(core, modules, AwtDirectoryPicker(window, core.dispatchers.main), scope) }
    WindowBindings(modules.registry, controllers.providers, controllers.palette, keys)
    AppMenu(
        controllers.projects,
        controllers.palette,
        controllers.missionControl,
        controllers.terminal,
        modules.projects,
    )
    AgentManagerApp(
        projects = controllers.projects,
        missionControl = controllers.missionControl,
        palette = controllers.palette,
        terminalPanel = { modifier ->
            TerminalArea(controllers.terminal, controllers.terminalKeys::onKey, modifier)
        },
    )
}

/** Registers the command [providers] and routes the window keys to [palette] while the window is shown. */
@Composable
private fun WindowBindings(
    registry: CommandRegistry,
    providers: List<CommandProvider>,
    palette: CommandPaletteController,
    keys: WindowKeys,
) {
    DisposableEffect(Unit) {
        val registrations = providers.map(registry::register)
        onDispose { registrations.forEach { it.close() } }
    }
    DisposableEffect(Unit) {
        val handle = keys.attach(palette::onKeyEvent)
        onDispose { handle.close() }
    }
}
