package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.commandpalette.api.CommandId
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.missioncontrol.ui.GlobalView
import dev.agentmanager.missioncontrol.ui.MissionControlIntent
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.NextSession
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.PreviousSession
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.RemoveSelected
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.RespawnSelected
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.SelectFilter
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.ShowView
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.StopSelected
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.TogglePreview
import dev.agentmanager.missioncontrol.ui.SessionActionsState
import dev.agentmanager.missioncontrol.ui.SessionFilter
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.ui.ProjectsMenuState

/** Session and navigation commands of the palette (AM-409, decision 1); none while a dialog is open. */
@Suppress("LongParameterList") // the streams of the palette and two callbacks (plan, decision 6)
internal fun sessionCommands(
    projects: List<Project>,
    menu: ProjectsMenuState,
    actions: SessionActionsState,
    view: GlobalView?,
    onIntent: (MissionControlIntent) -> Unit,
    openPalette: (CommandGroup) -> Unit,
): List<Command> {
    if (menu.dialogOpen || actions.dialogOpen) return emptyList()
    val session =
        listOf(
            sessionCommand("sessions.next", "command.nextSession", AppShortcuts.NEXT_SESSION, NextSession, onIntent),
            sessionCommand(
                "sessions.previous",
                "command.previousSession",
                AppShortcuts.PREVIOUS_SESSION,
                PreviousSession,
                onIntent,
            ),
        )
    val all = selectedSessionActions(actions, onIntent) + session + filtersOrViews(view, onIntent)
    return if (projects.isEmpty()) all else all + switchProject(openPalette)
}

private fun sessionCommand(
    id: String,
    titleKey: String,
    shortcut: Shortcut?,
    intent: MissionControlIntent,
    onIntent: (MissionControlIntent) -> Unit,
): Command = Command(CommandId(id), Strings[titleKey], CommandGroup.SESSION, emptyList(), shortcut) { onIntent(intent) }

/** Stop, Respawn, Remove and the preview of the selected session, offered by what the session allows now (AM-410). */
private fun selectedSessionActions(
    actions: SessionActionsState,
    onIntent: (MissionControlIntent) -> Unit,
): List<Command> =
    listOfNotNull(
        sessionCommand(
            "sessions.stop",
            "command.stopSession",
            AppShortcuts.STOP_SESSION,
            StopSelected,
            onIntent,
        ).copy(keywords = listOf("stop")).takeIf { actions.canStop },
        sessionCommand(
            "sessions.respawn",
            "command.respawnSession",
            null,
            RespawnSelected,
            onIntent,
        ).copy(keywords = listOf("respawn", "wznów")).takeIf { actions.canRespawn },
        sessionCommand(
            "sessions.remove",
            "command.removeSession",
            null,
            RemoveSelected,
            onIntent,
        ).copy(keywords = listOf("remove", "rm", "usuń")).takeIf { actions.canRemove },
        previewCommand(actions, onIntent).takeIf { actions.canPreview || actions.previewOpen },
    )

/** `space`: opens the output preview of the selected session, or closes the open one (AM-411). */
private fun previewCommand(
    actions: SessionActionsState,
    onIntent: (MissionControlIntent) -> Unit,
): Command =
    sessionCommand(
        "sessions.preview",
        if (actions.previewOpen) "command.closePreview" else "command.previewSession",
        AppShortcuts.PREVIEW_SESSION,
        TogglePreview,
        onIntent,
    ).copy(keywords = listOf("preview", "logs", "podgląd"))

/** Filter commands on the project page (none in a global view), view commands except the shown view (AM-407). */
private fun filtersOrViews(
    view: GlobalView?,
    onIntent: (MissionControlIntent) -> Unit,
): List<Command> {
    val filters =
        if (view != null) {
            emptyList()
        } else {
            listOf(
                filterCommand("all", "command.filterAll", SessionFilter.ALL, onIntent),
                filterCommand("active", "command.filterActive", SessionFilter.ACTIVE, onIntent),
                filterCommand("finished", "command.filterFinished", SessionFilter.FINISHED, onIntent),
            )
        }
    return filters + GlobalView.entries.filter { it != view }.map { viewCommand(it, onIntent) }
}

private fun viewCommand(
    view: GlobalView,
    onIntent: (MissionControlIntent) -> Unit,
): Command {
    val name = view.name.lowercase()
    val keywords =
        when (view) {
            GlobalView.WAITING -> listOf("waiting", "czeka")
            GlobalView.WORKING -> listOf("working")
            GlobalView.FAILED -> listOf("failed", "errors", "błąd")
        }
    return sessionCommand("sessions.view.$name", "command.view.$name", null, ShowView(view), onIntent)
        .copy(keywords = keywords)
}

private fun filterCommand(
    suffix: String,
    titleKey: String,
    filter: SessionFilter,
    onIntent: (MissionControlIntent) -> Unit,
): Command = sessionCommand("sessions.filter.$suffix", titleKey, null, SelectFilter(filter), onIntent)

private fun switchProject(openPalette: (CommandGroup) -> Unit): Command =
    Command(
        CommandId("projects.switch"),
        Strings["command.switchProject"],
        CommandGroup.PROJECT,
        listOf("switch", "projekt"),
        AppShortcuts.SWITCH_PROJECT,
    ) { openPalette(CommandGroup.GO_TO_PROJECT) }
