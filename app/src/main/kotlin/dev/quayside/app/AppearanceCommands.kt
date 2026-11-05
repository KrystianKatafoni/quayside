package dev.quayside.app

import dev.quayside.appearance.ui.AppearanceController
import dev.quayside.appearance.ui.AppearanceDialogState
import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
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
 * The palette command "Wygląd…" (`appearance.open`, FR-A3, AM-803); none while a project, session, settings or
 * Appearance dialog is open ([dialogOpen]).
 */
internal fun appearanceCommands(
    dialogOpen: Boolean,
    appearance: AppearanceController,
): List<Command> =
    if (dialogOpen) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("appearance.open"),
                Strings["command.appearance"],
                CommandGroup.APP,
                listOf("appearance", "theme", "motyw", "wyglad"),
            ) { appearance.open() },
        )
    }

/**
 * Offers [appearanceCommands], hidden while a dialog of the projects, Mission Control or Appearance is open. The
 * hiding is a guard only against a late `stateIn` (the open dialog's popup already blocks the palette).
 */
internal class AppearanceCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    appearance: AppearanceController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState, dialog: AppearanceDialogState? ->
            appearanceCommands(menu.dialogOpen || actions.dialogOpen || dialog != null, appearance)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, appearance.state, offered).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(controller.menu.value, missionControl.actions.value, appearance.state.value),
        )
}
