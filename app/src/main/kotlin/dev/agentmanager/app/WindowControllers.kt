package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.CommandProvider
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.missioncontrol.ui.MissionControlController
import dev.agentmanager.projects.ui.DirectoryPicker
import dev.agentmanager.projects.ui.ProjectsController
import dev.agentmanager.terminal.ui.TerminalController
import kotlinx.coroutines.CoroutineScope

/**
 * The controllers and command providers of one main window; they live as long as the window and run in [scope]
 * (AM-408, AM-503).
 */
internal class WindowControllers(
    core: CoreDeps,
    modules: AppModules,
    picker: DirectoryPicker,
    scope: CoroutineScope,
) {
    val projects = ProjectsController(modules.projects, modules.inspector, modules.sessions, picker, scope, core.logger)
    val missionControl =
        MissionControlController(modules.sessions, modules.projects, projects.selection, core.clock, scope, core.logger)
    val palette = CommandPaletteController(modules.registry, scope, core.logger)
    val terminal = TerminalController(modules.terminal.api, scope, core.logger, modules.terminal.shellLabel)

    /** ⌘ keys of a focused terminal, bridged to the palette and the shortcuts. */
    val terminalKeys = TerminalKeys(modules.registry, palette, scope)

    private val attacher = SessionAttacher(missionControl, terminal, modules.terminal.attachCommand, core.logger)

    init {
        terminal.followSessions(modules.sessions.monitor.snapshot, scope)
    }

    val providers: List<CommandProvider> =
        listOf(
            AppCommands(modules.projects, projects, missionControl, palette, scope),
            TerminalCommands(modules.projects, projects, missionControl, terminal, scope),
            AttachCommands(projects, missionControl, attacher, scope),
        )
}
