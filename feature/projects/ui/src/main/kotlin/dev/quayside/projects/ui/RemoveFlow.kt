package dev.quayside.projects.ui

import dev.quayside.projects.api.Project
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The remove dialog flow of [ProjectsController] (FR-P3, FR-P4): read the hooks, then remove, with or without
 * stopping the sessions, or uninstall our hooks first. [begin] opens a dialog before its step runs.
 */
internal class RemoveFlow(
    private val ui: MutableStateFlow<ProjectsUiState>,
    private val commands: ProjectCommands,
    private val hooks: ProjectHooks,
    private val begin: (Dialog, suspend () -> Unit) -> Unit,
) {
    /** Reads the hooks first; a result that arrives after "Anuluj" opens nothing. */
    fun request(project: Project) {
        begin(Dialog.Waiting.CHECKING_HOOKS) {
            val status = hooks.status(project.dir)
            // Defence against an adapter that ignores cancellation (no withContext inside).
            currentCoroutineContext().ensureActive()
            ui.update { it.copy(dialog = it.confirmRemove(project).copy(hooks = status)) }
        }
    }

    /** Runs [event] if it belongs to the remove dialog and returns true; a click that does not fit is ignored. */
    fun handle(
        event: SidebarEvent,
        dialog: Dialog?,
    ): Boolean {
        when (event) {
            SidebarEvent.UninstallHooks -> uninstallHooks(dialog)
            SidebarEvent.StopAndRemove -> confirmRemove(dialog, stop = true)
            SidebarEvent.RemoveKeepSessions -> confirmRemove(dialog, stop = false)
            else -> return false
        }
        return true
    }

    /** Only in the remove dialog that shows our hooks; the project stays listed and the dialog stays open. */
    private fun uninstallHooks(dialog: Dialog?) {
        if (dialog !is Dialog.ConfirmRemove || dialog.hooks != ProjectHooksState.PRESENT) return
        begin(Dialog.Waiting.BUSY) {
            val notice = commands.uninstallHooks(dialog.project)
            val status = hooks.status(dialog.project.dir)
            ui.update { it.copy(dialog = dialog.copy(hooks = status, hooksNotice = notice)) }
        }
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
}
