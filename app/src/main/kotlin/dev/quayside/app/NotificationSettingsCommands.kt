package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.notifications.ui.NotificationDialogState
import dev.quayside.notifications.ui.NotificationSettingsController
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsMenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The palette command "Ustawienia powiadomień" (`notifications.settings`, FR-N1, AM-704); none while a project or
 * session dialog or the settings dialog itself is open ([dialogOpen]).
 */
internal fun notificationSettingsCommands(
    dialogOpen: Boolean,
    settings: NotificationSettingsController,
): List<Command> =
    if (dialogOpen) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("notifications.settings"),
                Strings["command.notificationSettings"],
                CommandGroup.APP,
                listOf("notifications"),
            ) { settings.open() },
        )
    }

/**
 * Offers [notificationSettingsCommands], hidden while a dialog of the projects, Mission Control or notification
 * settings is open. The hiding is a guard only against a late `stateIn` (the open dialog's popup already blocks the
 * palette); [notificationSettingsCommands] carries the tested rule.
 */
internal class NotificationSettingsCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    settings: NotificationSettingsController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState, dialog: NotificationDialogState? ->
            notificationSettingsCommands(menu.dialogOpen || actions.dialogOpen || dialog != null, settings)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, settings.state, offered).stateIn(
            scope,
            SharingStarted.Eagerly,
            offered(controller.menu.value, missionControl.actions.value, settings.state.value),
        )
}
