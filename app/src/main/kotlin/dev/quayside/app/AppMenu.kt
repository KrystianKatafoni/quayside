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
    val missionControl = controllers.missionControl
    val terminal = controllers.terminal
    val terminalSettings = controllers.terminalSettings
    val menu by projects.menu.collectAsState()
    val list by projectsApi.projects.collectAsState()
    val selection by projects.selection.collectAsState()
    val paletteOpen by palette.isOpen.collectAsState()
    val sessionActions by missionControl.actions.collectAsState()
    val settingsDialog by terminalSettings.state.collectAsState()
    val enabled = AppMenuState.of(menu, paletteOpen, sessionActions.dialogOpen || settingsDialog != null)
    MenuBar {
        Menu(Strings["menu.projects"]) { ProjectItems(projects, enabled) }
        Menu(Strings["menu.terminal"]) {
            TerminalItems(terminal, terminalSettings, selectedTerminalProject(list, selection), !enabled.palette)
        }
        Menu(Strings["menu.view"]) {
            Item(
                text = Strings["menu.palette"],
                enabled = enabled.palette,
                shortcut = AppShortcuts.PALETTE.toKeyShortcut(),
                onClick = { palette.open() },
            )
        }
    }
}

@Composable
private fun MenuScope.ProjectItems(
    projects: ProjectsController,
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
        text = Strings["menu.removeProject"],
        enabled = enabled.remove,
        shortcut = AppShortcuts.REMOVE_PROJECT.toKeyShortcut(),
        onClick = { projects.onIntent(ProjectsIntent.RemoveSelectedRequested) },
    )
}
