package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.missioncontrol.ui.HistoryDialogState
import dev.quayside.missioncontrol.ui.HistorySettingsController
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsMenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The palette command "Stare sesje z dysku…" (`sessions.history`, FR-P9, AM-312); none while a project or session
 * dialog or the window itself is open ([dialogOpen]). The id is fixed: it holds no name, path or title (ENG-C8).
 */
internal fun sessionHistoryCommands(
    dialogOpen: Boolean,
    history: HistorySettingsController,
): List<Command> =
    if (dialogOpen) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("sessions.history"),
                Strings["command.sessionHistory"],
                CommandGroup.APP,
                listOf("history", "disk", "sessions", "limit"),
            ) { history.open() },
        )
    }

/**
 * Offers [sessionHistoryCommands], hidden while a dialog of the projects, Mission Control or the old sessions is
 * open. The hiding is a guard only against a late `stateIn` (the open dialog's popup already blocks the palette);
 * [sessionHistoryCommands] carries the tested rule.
 */
internal class SessionHistoryCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    history: HistorySettingsController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState, dialog: HistoryDialogState? ->
            sessionHistoryCommands(menu.dialogOpen || actions.dialogOpen || dialog != null, history)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, history.state, offered).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(controller.menu.value, missionControl.actions.value, history.state.value),
        )
}
