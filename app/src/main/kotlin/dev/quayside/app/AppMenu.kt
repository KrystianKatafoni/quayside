package dev.quayside.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.commandpalette.ui.toKeyShortcut
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.terminal.ui.TerminalController
import dev.quayside.terminal.ui.TerminalSettingsController

/**
 * The window menu: "Projects", "Terminal" and "View". Items are enabled by [AppMenuState.of]; shortcuts come from
 * [AppShortcuts], the same source as the palette rows.
 */
@Composable
internal fun FrameWindowScope.AppMenu(
    controllers: WindowControllers,
    projectsApi: ProjectsApi,
) {
    val projects = controllers.projects
    val palette = controllers.palette
    val terminal = controllers.terminal
    val terminalSettings = controllers.terminalSettings
    val list by projectsApi.projects.collectAsState()
    val selection by projects.selection.collectAsState()
    val enabled = menuStateOf(controllers, projectsApi)
    MenuBar {
        Menu(Strings["menu.projects"]) {
            ProjectItems(projects, controllers.newAgent, enabled)
        }
        Menu(Strings["menu.terminal"]) {
            TerminalItems(terminal, terminalSettings, selectedTerminalProject(list, selection), !enabled.palette)
        }
        Menu(
            Strings["menu.view"],
        ) { ViewItems(palette, controllers, enabled) }
    }
}

/** Which menu items (and the "Nowy agent" button of the top bar) are enabled now. */
@Composable
internal fun menuStateOf(
    controllers: WindowControllers,
    projectsApi: ProjectsApi,
): AppMenuState {
    val list by projectsApi.projects.collectAsState()
    return menuStateOf(controllers.projects, controllers.palette, anyDialogOpen(controllers), list.isNotEmpty())
}

/** [AppMenuState.of] over the live state of [projects] and [palette]; the window of the tests calls it directly. */
@Composable
internal fun menuStateOf(
    projects: ProjectsController,
    palette: CommandPaletteController,
    dialogOpen: Boolean,
    hasProjects: Boolean,
): AppMenuState {
    val menu by projects.menu.collectAsState()
    val paletteOpen by palette.isOpen.collectAsState()
    return AppMenuState.of(menu, paletteOpen, dialogOpen, hasProjects)
}

/**
 * The palette, the notification settings (AM-704), the summaries settings (AM-606), the old sessions (AM-312), the
 * appearance (AM-803) and the hooks (AM-607); all wait for dialogs like the palette.
 */
@Composable
private fun MenuScope.ViewItems(
    palette: CommandPaletteController,
    controllers: WindowControllers,
    enabled: AppMenuState,
) {
    Item(
        text = Strings["menu.palette"],
        enabled = enabled.palette,
        shortcut = AppShortcuts.PALETTE.toKeyShortcut(),
        onClick = { palette.open() },
    )
    SessionSettingsItems(controllers, enabled)
    Item(
        text = Strings["menu.appearance"],
        enabled = enabled.palette,
        onClick = { controllers.appearance.open() },
    )
    Item(
        text = Strings["menu.hookSetup"],
        enabled = enabled.palette,
        onClick = { controllers.hookSetup.open() },
    )
}

/** The settings of notifications, summaries and old sessions (AM-704, AM-606, AM-312). */
@Composable
private fun MenuScope.SessionSettingsItems(
    controllers: WindowControllers,
    enabled: AppMenuState,
) {
    Item(
        text = Strings["menu.notificationSettings"],
        enabled = enabled.palette,
        onClick = { controllers.notificationSettings.open() },
    )
    Item(
        text = Strings["menu.summarySettings"],
        enabled = enabled.palette,
        onClick = { controllers.summarySettings.open() },
    )
    Item(
        text = Strings["menu.sessionHistory"],
        enabled = enabled.palette,
        onClick = { controllers.historySettings.open() },
    )
}

@Composable
private fun MenuScope.ProjectItems(
    projects: ProjectsController,
    newAgent: NewAgentController,
    enabled: AppMenuState,
) {
    Item(
        text = Strings["menu.addProject"],
        enabled = enabled.add,
        shortcut = AppShortcuts.ADD_PROJECT.toKeyShortcut(),
        onClick = { projects.onIntent(ProjectsIntent.AddRequested) },
    )
    Item(
        text = Strings["menu.renameProject"],
        enabled = enabled.rename,
        onClick = { projects.onIntent(ProjectsIntent.RenameSelectedRequested) },
    )
    Item(
        text = Strings["menu.subfolders"],
        enabled = enabled.subfolders,
        onClick = { projects.onIntent(ProjectsIntent.EditSubfoldersRequested) },
    )
    Item(
        text = Strings["menu.removeProject"],
        enabled = enabled.remove,
        shortcut = AppShortcuts.REMOVE_PROJECT.toKeyShortcut(),
        onClick = { projects.onIntent(ProjectsIntent.RemoveSelectedRequested) },
    )
    Item(
        text = Strings["menu.newAgent"],
        enabled = enabled.newAgent,
        shortcut = AppShortcuts.NEW_AGENT.toKeyShortcut(),
        onClick = { openNewAgent(projects, newAgent) },
    )
}

/** A dialog of Mission Control, the settings, the hooks or "Nowy agent" is open: it takes the keys of the window. */
@Composable
private fun anyDialogOpen(controllers: WindowControllers): Boolean {
    val sessionActions by controllers.missionControl.actions.collectAsState()
    val settingsDialog by controllers.terminalSettings.state.collectAsState()
    val notificationDialog by controllers.notificationSettings.state.collectAsState()
    val summaryDialog by controllers.summarySettings.state.collectAsState()
    val historyDialog by controllers.historySettings.state.collectAsState()
    val hookDialog by controllers.hookSetup.state.collectAsState()
    val newAgentOpen by controllers.newAgent.isOpen.collectAsState()
    val appearanceDialog by controllers.appearance.state.collectAsState()
    return sessionActions.dialogOpen ||
        appearanceDialog != null ||
        settingsDialog != null ||
        notificationDialog != null ||
        summaryDialog != null ||
        historyDialog != null ||
        hookDialog != null ||
        newAgentOpen
}
