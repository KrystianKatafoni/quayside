package dev.quayside.app

import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.notifications.ui.NotificationSettingsController
import dev.quayside.projects.ui.DirectoryPicker
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.terminal.ui.TerminalController
import dev.quayside.terminal.ui.TerminalSettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The controllers and command providers of one main window; they live as long as the window and run in [scope]
 * (AM-408, AM-503).
 */
internal class WindowControllers(
    core: CoreDeps,
    modules: AppModules,
    picker: DirectoryPicker,
    scope: CoroutineScope,
    hooks: HookDeps,
) {
    val projects = ProjectsController(modules.projects, modules.inspector, modules.sessions, picker, scope, core.logger)
    val missionControl =
        MissionControlController(modules.sessions, modules.projects, projects.selection, core.clock, scope, core.logger)
    val palette = CommandPaletteController(modules.registry, scope, core.logger)
    private val shellStart =
        SettingsShellStart(
            modules.terminal.settings,
            modules.terminal.loginShell,
            core.homeDir,
            selectedSessionDirOf(missionControl, modules.sessions.monitor.snapshot),
        )
    val terminal = TerminalController(modules.terminal.api, scope, core.logger, shellStart)

    val terminalSettings = TerminalSettingsController(modules.terminal.repo, scope)

    val notificationSettings =
        NotificationSettingsController(modules.notifications.settings, modules.notifications.voices, scope)

    private val notificationDialogOpen = notificationSettings.dialogOpen(scope)

    val hookSetup = HookSetupController(hooks.installer, hooks.offer, scope, core.logger)

    val newAgent =
        NewAgentController(
            modules.sessions.commands,
            modules.projects,
            scope,
            core.logger,
            selectStartedSession(projects, missionControl),
        )

    private val settingsDialogOpen =
        combine(terminalSettings.state, notificationDialogOpen) { terminal, notification ->
            terminal != null || notification
        }.stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    /** What hides the hooks command: the settings dialogs and "Nowy agent" (the hooks dialog hides it itself). */
    private val hooksBlocked =
        combine(settingsDialogOpen, newAgent.isOpen) { settings, agent -> settings || agent }
            .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    /** What hides "Nowy agent" besides its own dialog: the settings dialogs and the hooks dialog. */
    private val newAgentBlocked =
        combine(settingsDialogOpen, hookSetup.state) { settings, hooks -> settings || hooks != null }
            .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    /** ⌘ keys of a focused terminal, bridged to the palette and the shortcuts. */
    val terminalKeys = TerminalKeys(modules.registry, palette, scope)

    val attacher = SessionAttacher(missionControl, terminal, modules.terminal.attachCommand, core.logger)

    init {
        terminal.followSessions(modules.sessions.monitor.snapshot, scope)
    }

    val providers: List<CommandProvider> =
        listOf(
            AppCommands(modules.projects, projects, missionControl, palette, scope),
            TerminalCommands(modules.projects, projects, missionControl, terminal, scope),
            AttachCommands(projects, missionControl, attacher, scope),
            TerminalSettingsCommands(projects, missionControl, terminalSettings, scope, notificationDialogOpen),
            NotificationSettingsCommands(projects, missionControl, notificationSettings, scope),
            HookSetupCommands(projects, missionControl, hookSetup, scope, hooksBlocked),
            NewAgentCommands(projects, missionControl, modules.projects, newAgent, scope, newAgentBlocked),
        )
}
