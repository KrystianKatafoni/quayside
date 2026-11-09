package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsMenuState
import dev.quayside.summaries.ui.SummaryDialogState
import dev.quayside.summaries.ui.SummarySettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The palette command "Ustawienia podsumowań" (`summaries.settings`, FR-S7, AM-606); none while a project or session
 * dialog or the settings dialog itself is open ([dialogOpen]).
 */
internal fun summarySettingsCommands(
    dialogOpen: Boolean,
    settings: SummarySettingsController,
): List<Command> =
    if (dialogOpen) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("summaries.settings"),
                Strings["command.summarySettings"],
                CommandGroup.APP,
                listOf("summaries", "haiku"),
            ) { settings.open() },
        )
    }

/**
 * Offers [summarySettingsCommands], hidden while a dialog of the projects, Mission Control or summary settings is
 * open. The hiding is a guard only against a late `stateIn` (the open dialog's popup already blocks the palette);
 * [summarySettingsCommands] carries the tested rule.
 */
internal class SummarySettingsCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    settings: SummarySettingsController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState, dialog: SummaryDialogState? ->
            summarySettingsCommands(menu.dialogOpen || actions.dialogOpen || dialog != null, settings)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, settings.state, offered).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(controller.menu.value, missionControl.actions.value, settings.state.value),
        )
}
