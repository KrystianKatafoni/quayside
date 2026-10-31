package dev.quayside.projects.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder of the projects sidebar. Runs every command in [scope] (the window scope, Main dispatcher);
 * at most one dialog flow at a time, further requests are ignored until it ends. Never throws: errors
 * become fixed texts without paths (ENG-C8). Logs only fixed categories, counts, project and session ids.
 *
 * @param sessions read side for the per-project counts (only `monitor.snapshot` is collected, never `events`),
 *   `commands` for "stop sessions and remove".
 * @param picker the folder chooser; called from [scope].
 * @param scope confined to one thread, like the window's Main dispatcher: the controller is not thread-safe.
 */
public class ProjectsController(
    private val projects: ProjectsApi,
    private val inspector: ProjectInspector,
    private val sessions: SessionsApi,
    private val picker: DirectoryPicker,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val commands = ProjectCommands(projects, sessions.commands, logger)

    private val ui =
        MutableStateFlow(
            ProjectsUiState(
                projects = projects.projects.value,
                banner = projects.storageIssue.value?.let(ProjectMessages::of),
            ),
        )

    /** The running step of a dialog flow, kept only to cancel an inspection or a listing. */
    private var running: Job? = null

    /** Read by the composables only. */
    internal val state: StateFlow<ProjectsUiState> = ui.asStateFlow()

    /** The project selected in the sidebar, null when none; read by `app` for Mission Control (AM-405). */
    public val selection: StateFlow<ProjectId?> =
        ui.map { it.selected }.stateIn(scope, SharingStarted.Eagerly, ui.value.selected)

    /** Enabled window-menu commands. */
    public val menu: StateFlow<ProjectsMenuState> =
        ui.map(ProjectsUiState::menu).stateIn(scope, SharingStarted.Eagerly, ui.value.menu())

    init {
        scope.launch {
            combine(projects.projects, sessions.monitor.snapshot) { list, snapshot -> list to snapshot }
                .collect { (list, snapshot) ->
                    ui.update { it.withProjects(list).copy(sessions = SessionsView.of(list, snapshot)) }
                }
        }
    }

    /** Handles a sidebar or window-menu intent; ignored while a dialog flow is in progress. */
    public fun onIntent(intent: ProjectsIntent) {
        val current = ui.value
        if (current.dialog != null) return
        when (intent) {
            ProjectsIntent.AddRequested -> {
                begin(Dialog.Picking) { pickAndInspect() }
            }

            ProjectsIntent.RemoveSelectedRequested -> {
                current.selectedProject()?.let { ui.update { s -> s.copy(dialog = s.confirmRemove(it)) } }
            }

            ProjectsIntent.RenameSelectedRequested -> {
                current.selected?.let { id -> ui.update { it.reduce(SidebarEvent.RenameRequested(id)) } }
            }

            is ProjectsIntent.Select -> {
                if (current.projects.any { it.id == intent.id }) ui.update { it.reduce(SidebarEvent.Select(intent.id)) }
            }

            ProjectsIntent.ClearSelection -> {
                ui.update { it.copy(selected = null) }
            }
        }
    }

    /** Clicks inside the sidebar and its dialogs; a click that does not fit the open dialog is ignored. */
    internal fun onEvent(event: SidebarEvent) {
        val dialog = ui.value.dialog
        when {
            event == SidebarEvent.Dismiss -> dismiss(dialog)
            event == SidebarEvent.ConfirmAdd && dialog is Dialog.ConfirmAdd -> confirmAdd(dialog)
            event == SidebarEvent.ConfirmRename && dialog is Dialog.Rename -> confirmRename(dialog)
            event == SidebarEvent.StopAndRemove -> confirmRemove(dialog, stop = true)
            event == SidebarEvent.RemoveKeepSessions -> confirmRemove(dialog, stop = false)
            else -> ui.update { it.reduce(event) }
        }
    }

    /** Closes the dialog and cancels an inspection or listing; the folder chooser and a confirmed command run on. */
    private fun dismiss(dialog: Dialog?) {
        if (dialog == null || dialog == Dialog.Picking || dialog == Dialog.Waiting.BUSY) return
        running?.cancel()
        ui.update { it.copy(dialog = null) }
    }

    /** Stopping needs active sessions in a fresh snapshot: without them only removing is offered. */
    private fun confirmRemove(
        dialog: Dialog?,
        stop: Boolean,
    ) {
        if (dialog !is Dialog.ConfirmRemove) return
        if (stop && (dialog.active?.active ?: 0) == 0) return
        begin(Dialog.Waiting.BUSY) {
            val failure = commands.remove(dialog.project, stop)
            ui.update { it.copy(dialog = failure?.let(Dialog::Message)) }
        }
    }

    /** An unchanged name closes the dialog; otherwise saves, and an error keeps the dialog with its fixed text. */
    private fun confirmRename(dialog: Dialog.Rename) {
        if (dialog.draft.trim() == dialog.project.displayName) {
            ui.update { it.copy(dialog = null) }
        } else {
            begin(Dialog.Waiting.BUSY) {
                val failure = commands.rename(dialog.project.id, dialog.draft)
                ui.update { it.copy(dialog = if (failure == null) null else dialog.copy(error = failure)) }
            }
        }
    }

    /**
     * Opens [dialog] before [step] is launched, in the same call: a second click that arrives before the step
     * runs already sees [dialog] and is ignored, so a command never runs twice.
     */
    private fun begin(
        dialog: Dialog,
        step: suspend () -> Unit,
    ) {
        ui.update { it.copy(dialog = dialog) }
        running = scope.launch { step() }
    }

    /** Folder chooser, then the read-only inspection; a cancelled chooser changes nothing. */
    private suspend fun pickAndInspect() {
        val dir = picker.pick()
        if (dir == null) {
            ui.update { it.copy(dialog = null) }
            return
        }
        ui.update { it.copy(dialog = Dialog.Waiting.INSPECTING) }
        when (val inspected = inspector.inspect(dir)) {
            is Outcome.Err -> fail("project inspection failed", inspected.error)
            is Outcome.Ok -> offer(inspected.value)
        }
    }

    private fun confirmAdd(dialog: Dialog.ConfirmAdd) = begin(Dialog.Waiting.BUSY) { add(dialog) }

    /** "Add" in [dialog]: an error keeps the dialog open with its fixed text. */
    private suspend fun add(dialog: Dialog.ConfirmAdd) {
        when (val added = projects.add(dialog.report.dir)) {
            is Outcome.Ok -> {
                logger.info("project added", "projectId" to added.value.id.value)
                ui.update { it.copy(selected = added.value.id, dialog = null) }
            }

            is Outcome.Err -> {
                logger.warn("project add failed", "reason" to added.error.category())
                ui.update { it.copy(dialog = dialog.copy(error = ProjectMessages.of(added.error))) }
            }
        }
    }

    /** A folder already on the list selects its project instead of adding it again. */
    private fun offer(report: DirectoryReport) {
        val existing = report.alreadyAdded
        if (existing == null) {
            ui.update { it.copy(dialog = Dialog.ConfirmAdd(report)) }
        } else {
            val message = Dialog.Message(ProjectMessages.of(ProjectError.AlreadyAdded(existing)))
            ui.update { it.copy(selected = existing, dialog = message) }
        }
    }

    private fun fail(
        message: String,
        error: ProjectError,
    ) {
        logger.warn(message, "reason" to error.category())
        ui.update { it.copy(dialog = Dialog.Message(ProjectMessages.of(error))) }
    }
}
