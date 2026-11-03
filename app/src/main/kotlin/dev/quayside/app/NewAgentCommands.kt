package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsMenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The palette command "Nowy agent…" (`sessions.new`, FR-S3, AM-608); none without projects or while a dialog is
 * open ([dialogOpen]).
 */
internal fun newAgentCommands(
    hasProjects: Boolean,
    dialogOpen: Boolean,
    open: () -> Unit,
): List<Command> =
    if (dialogOpen || !hasProjects) {
        emptyList()
    } else {
        listOf(
            Command(
                CommandId("sessions.new"),
                Strings["command.newAgent"],
                CommandGroup.SESSION,
                listOf("new", "agent", "start", "nowy"),
                AppShortcuts.NEW_AGENT,
            ) { open() },
        )
    }

/**
 * Offers [newAgentCommands] for the live project list, hidden while a dialog of the projects, Mission Control,
 * the other settings ([otherDialogOpen]) or "Nowy agent" is open; opens the dialog with the selected project.
 * The hiding guards against a late `stateIn`; the open dialog's popup already blocks the palette (ADR 0011).
 */
internal class NewAgentCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    projects: ProjectsApi,
    newAgent: NewAgentController,
    scope: CoroutineScope,
    otherDialogOpen: StateFlow<Boolean>,
) : CommandProvider {
    private val offered =
        { list: List<Project>, menu: ProjectsMenuState, dialogOpen: Boolean, other: Boolean, actionsDialog: Boolean ->
            newAgentCommands(list.isNotEmpty(), menu.dialogOpen || dialogOpen || other || actionsDialog) {
                newAgent.open(controller.selection.value)
            }
        }

    override val commands: StateFlow<List<Command>> =
        combine(
            projects.projects,
            controller.menu,
            newAgent.isOpen,
            otherDialogOpen,
            missionControl.actions,
        ) { list, menu, open, other, actions -> offered(list, menu, open, other, actions.dialogOpen) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                offered(
                    projects.projects.value,
                    controller.menu.value,
                    newAgent.isOpen.value,
                    otherDialogOpen.value,
                    missionControl.actions.value.dialogOpen,
                ),
            )
}

/**
 * `onStarted` of the "Nowy agent" dialog: the sidebar selects [project] and Mission Control selects the new session
 * on its page with filter "Wszystkie" (AM-608, rule 6).
 */
internal fun selectStartedSession(
    projects: ProjectsController,
    missionControl: MissionControlController,
): (ProjectId, SessionId) -> Unit =
    { project, id ->
        projects.onIntent(ProjectsIntent.Select(project))
        missionControl.onIntent(MissionControlIntent.SelectSession(project, id))
    }
