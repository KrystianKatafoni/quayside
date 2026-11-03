package dev.quayside.app

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

/** The palette command "Hooki Claude Code" (`hooks.setup`, NFR-5, AM-607); none while a dialog is open. */
internal fun hookSetupCommands(
    dialogOpen: Boolean,
    setup: HookSetupController,
): List<Command> =
    if (dialogOpen) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("hooks.setup"),
                Strings["command.hookSetup"],
                CommandGroup.APP,
                listOf("hooks"),
            ) { setup.open() },
        )
    }

/**
 * Offers [hookSetupCommands], hidden while a dialog of the projects, Mission Control, the other settings
 * ([otherDialogOpen]) or the hooks dialog is open. The hiding guards against a late `stateIn`; the open dialog's
 * popup already blocks the palette (ADR 0011).
 */
internal class HookSetupCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    setup: HookSetupController,
    scope: CoroutineScope,
    otherDialogOpen: StateFlow<Boolean>,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState, dialog: HookDialogState?, other: Boolean ->
            hookSetupCommands(menu.dialogOpen || actions.dialogOpen || dialog != null || other, setup)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, setup.state, otherDialogOpen, offered).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(controller.menu.value, missionControl.actions.value, setup.state.value, otherDialogOpen.value),
        )
}
