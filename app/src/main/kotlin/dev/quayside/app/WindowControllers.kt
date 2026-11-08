package dev.quayside.app

import androidx.compose.ui.unit.dp
import dev.quayside.appearance.ui.AppearanceController
import dev.quayside.appearance.ui.ThemeFilePicker
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.notifications.ui.NotificationSettingsController
import dev.quayside.projects.ui.DirectoryPicker
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsPorts
import dev.quayside.summaries.ui.SummarySettingsController
import dev.quayside.terminal.ui.TerminalController
import dev.quayside.terminal.ui.TerminalSettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The system file dialogs of the window: folders for projects, files for themes (AM-804). */
internal class WindowPickers(
    val directory: DirectoryPicker,
    val themeFile: ThemeFilePicker,
)

/**
 * The controllers and command providers of one main window; they live as long as the window and run in [scope]
 * (AM-408, AM-503).
 */
internal class WindowControllers(
    core: CoreDeps,
    modules: AppModules,
    pickers: WindowPickers,
    scope: CoroutineScope,
    hooks: HookDeps,
) {
    val projects =
        ProjectsController(
            modules.projects.api,
            modules.projects.inspector,
            modules.sessions,
            ProjectsPorts(pickers.directory, hooks.projectHooks),
            scope,
            core.logger,
        )
    val missionControl =
        MissionControlController(
            modules.sessions,
            modules.projects.api,
            projects.focus,
            core.clock,
            scope,
            core.logger,
        )
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
        NotificationSettingsController(
            modules.settings.notifications.settings,
            modules.settings.notifications.voices,
            modules.settings.notifications.preview,
            modules.projects.api.projects,
            scope,
        )

    private val notificationDialogOpen = notificationSettings.dialogOpen(scope)

    val summarySettings = SummarySettingsController(modules.settings.summaries, scope)

    val appearance =
        AppearanceController(
            modules.appearance.api,
            modules.terminal.repo,
            modules.appearance.files,
            pickers.themeFile,
            scope,
        )

    val hookSetup = HookSetupController(hooks.installer, hooks.offer, hooks.targets, scope, core.logger)

    val newAgent =
        NewAgentController(
            modules.sessions.commands,
            modules.projects.api,
            scope,
            core.logger,
            selectStartedSession(projects, missionControl),
        )

    private val settingsDialogOpen =
        combine(
            combine(terminalSettings.state, notificationDialogOpen, appearance.state) { terminal, notification, look ->
                terminal != null || notification || look != null
            },
            summarySettings.isOpen,
        ) { others, summaries -> others || summaries }.stateIn(scope, SharingStarted.Eagerly, initialValue = false)

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

    val attacher =
        SessionAttacher(missionControl, terminal, modules.terminal.attachCommand, core.logger) {
            projects.onIntent(ProjectsIntent.Select(it))
        }

    /** The terminal height of the list mode (AM-819), from what `main` read of `window.json`. */
    val pane =
        modules.settings.layout.let {
            TerminalPaneHeight(it.initial.terminalHeight.dp, it.store, scope, core.logger)
        }

    /** Which layout the window shows (AM-809); leaves the terminal mode when the last tab is gone. */
    val mode = WindowModeState { terminal.menu.value.hasTabs }

    init {
        terminal.followSessions(modules.sessions.monitor.snapshot, scope)
        mode.follow(terminal.menu, scope)
    }

    val providers: List<CommandProvider> =
        listOf(
            AppCommands(modules.projects.api, projects, missionControl, palette, scope),
            TerminalCommands(modules.projects.api, projects, missionControl, terminal, scope),
            AttachCommands(projects, missionControl, attacher, scope),
            TerminalSettingsCommands(projects, missionControl, terminalSettings, scope, notificationDialogOpen),
            NotificationSettingsCommands(projects, missionControl, notificationSettings, scope),
            SummarySettingsCommands(projects, missionControl, summarySettings, scope),
            AppearanceCommands(projects, missionControl, appearance, scope),
            HookSetupCommands(projects, missionControl, hookSetup, scope, hooksBlocked),
            NewAgentCommands(projects, missionControl, modules.projects.api, newAgent, scope, newAgentBlocked),
            WindowModeCommands(mode, scope),
        )
}
