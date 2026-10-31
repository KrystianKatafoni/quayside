package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsMenuState
import dev.quayside.terminal.ui.SplitAxis
import dev.quayside.terminal.ui.TerminalController
import dev.quayside.terminal.ui.TerminalMenuState
import dev.quayside.terminal.ui.TerminalProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The project selected in the sidebar as a terminal project; null without a selection. */
internal fun selectedTerminalProject(
    list: List<Project>,
    selected: ProjectId?,
): TerminalProject? = list.firstOrNull { it.id == selected }?.let(::terminalProjectOf)

/** [project] as a terminal project: the tab group id, its title and the working dir. */
internal fun terminalProjectOf(project: Project): TerminalProject =
    TerminalProject(project.id, project.displayName, project.dir)

/** Id and title key of the one command that toggles the maximize, by the state it leads to. */
private val MAXIMIZE = "terminal.maximize" to "command.maximizePane"
private val RESTORE = "terminal.restore" to "command.restorePanes"

private fun terminalCommand(
    id: String,
    titleKey: String,
    shortcut: Shortcut? = null,
    action: () -> Unit,
): Command = Command(CommandId(id), Strings[titleKey], CommandGroup.APP, listOf("terminal"), shortcut) { action() }

/**
 * Terminal commands of the palette (AM-503, decision 10); none while a project dialog is open. Each is offered
 * only when it can work now: [project] selected, [state] allows the split, the pane switch, the maximize.
 */
internal fun terminalCommands(
    project: TerminalProject?,
    dialogOpen: Boolean,
    state: TerminalMenuState,
    terminal: TerminalController,
): List<Command> {
    if (dialogOpen) return emptyList()
    val newShell =
        project?.let {
            terminalCommand("terminal.newShell", "command.newShell", AppShortcuts.NEW_SHELL) { terminal.openShell(it) }
        }
    val splits =
        listOf(
            terminalCommand("terminal.splitSideBySide", "command.splitSideBySide", AppShortcuts.SPLIT_SIDE_BY_SIDE) {
                terminal.split(SplitAxis.SIDE_BY_SIDE)
            },
            terminalCommand("terminal.splitStacked", "command.splitStacked", AppShortcuts.SPLIT_STACKED) {
                terminal.split(SplitAxis.STACKED)
            },
        ).filter { state.canSplit }
    return listOfNotNull(newShell) + splits + paneCommands(state, terminal) + closeCommand(state, terminal)
}

private fun paneCommands(
    state: TerminalMenuState,
    terminal: TerminalController,
): List<Command> {
    if (!state.canSwitchPane) return emptyList()
    val (id, titleKey) = if (state.maximized) RESTORE else MAXIMIZE
    return listOf(
        terminalCommand("terminal.nextPane", "command.nextPane") { terminal.focusNext() },
        terminalCommand("terminal.previousPane", "command.previousPane") { terminal.focusPrevious() },
        terminalCommand(id, titleKey, AppShortcuts.MAXIMIZE_PANE) { terminal.toggleMaximized() },
    )
}

private fun closeCommand(
    state: TerminalMenuState,
    terminal: TerminalController,
): List<Command> =
    if (state.hasTabs) {
        listOf(terminalCommand("terminal.closeTab", "command.closeTerminalTab") { terminal.closeActive() })
    } else {
        emptyList()
    }

/** Offers [terminalCommands] for the selected project, the dialogs of Mission Control and projects, and the panel. */
internal class TerminalCommands(
    projects: ProjectsApi,
    controller: ProjectsController,
    missionControl: MissionControlController,
    terminal: TerminalController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        {
            list: List<Project>,
            selected: ProjectId?,
            menu: ProjectsMenuState,
            actions: SessionActionsState,
            state: TerminalMenuState,
            ->
            val project = selectedTerminalProject(list, selected)
            terminalCommands(project, menu.dialogOpen || actions.dialogOpen, state, terminal)
        }

    override val commands: StateFlow<List<Command>> =
        combine(
            projects.projects,
            controller.selection,
            controller.menu,
            missionControl.actions,
            terminal.menu,
            offered,
        ).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(
                projects.projects.value,
                controller.selection.value,
                controller.menu.value,
                missionControl.actions.value,
                terminal.menu.value,
            ),
        )
}
