package dev.agentmanager.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.commandpalette.ui.toKeyShortcut
import dev.agentmanager.missioncontrol.ui.MissionControlController
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.projects.ui.ProjectsController
import dev.agentmanager.projects.ui.ProjectsIntent
import dev.agentmanager.terminal.ui.TerminalController

/**
 * The window menu: "Projects", "Terminal" and "View". Items are enabled by [AppMenuState.of]; shortcuts come from
 * [AppShortcuts], the same source as the palette rows.
 */
@Composable
internal fun FrameWindowScope.AppMenu(
    projects: ProjectsController,
    palette: CommandPaletteController,
    missionControl: MissionControlController,
    terminal: TerminalController,
    projectsApi: ProjectsApi,
) {
    val menu by projects.menu.collectAsState()
    val list by projectsApi.projects.collectAsState()
    val selection by projects.selection.collectAsState()
    val paletteOpen by palette.isOpen.collectAsState()
    val sessionActions by missionControl.actions.collectAsState()
    val enabled = AppMenuState.of(menu, paletteOpen, sessionActions.dialogOpen)
    MenuBar {
        Menu(Strings["menu.projects"]) { ProjectItems(projects, enabled) }
        Menu(Strings["menu.terminal"]) {
            TerminalItems(terminal, selectedTerminalProject(list, selection), blocked = !enabled.palette)
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
