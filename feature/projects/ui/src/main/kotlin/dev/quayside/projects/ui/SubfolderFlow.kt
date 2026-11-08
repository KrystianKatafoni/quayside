package dev.quayside.projects.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.api.SubfolderName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The subfolders dialog flow of [ProjectsController] (FR-P7): list the subdirectories, then save the checked ones.
 * [begin] opens a dialog before its step runs (see `ProjectsController.begin`). Logs ids, counts and fixed categories
 * only: subfolder names hold fragments of paths (ENG-C8).
 */
internal class SubfolderFlow(
    private val ui: MutableStateFlow<ProjectsUiState>,
    private val projects: ProjectsApi,
    private val inspector: ProjectInspector,
    private val logger: Logger,
    private val begin: (Dialog, suspend () -> Unit) -> Unit,
) {
    /** Lists the subdirectories of [id], then opens the dialog; ignored with a dialog open or for an unlisted id. */
    fun request(id: ProjectId) {
        val state = ui.value
        val project = state.projects.find { it.id == id }
        if (state.dialog != null || project == null) return
        begin(Dialog.Waiting.LISTING) { list(project) }
    }

    /** Runs [event] if it belongs to this flow and returns true; "Zapisz" outside its dialog is ignored. */
    fun handle(
        event: SidebarEvent,
        dialog: Dialog?,
    ): Boolean {
        when {
            event is SidebarEvent.EditSubfoldersRequested -> request(event.id)
            event == SidebarEvent.SaveSubfolders -> (dialog as? Dialog.Subfolders)?.let(::save)
            else -> return false
        }
        return true
    }

    /** Stores the checked lines; an error keeps the dialog with its fixed text. */
    private fun save(dialog: Dialog.Subfolders) {
        begin(Dialog.Waiting.BUSY) {
            val names = dialog.choices.map { it.name }.filter { it in dialog.checked }
            when (val saved = projects.setSubfolders(dialog.project.id, names)) {
                is Outcome.Ok -> {
                    logger.info(
                        "project subfolders saved",
                        "projectId" to dialog.project.id.value,
                        "count" to names.size.toString(),
                    )
                    ui.update { it.copy(dialog = null) }
                }

                is Outcome.Err -> {
                    logger.warn("project subfolders save failed", "reason" to saved.error.category())
                    ui.update { it.copy(dialog = dialog.copy(error = ProjectMessages.of(saved.error))) }
                }
            }
        }
    }

    private suspend fun list(project: Project) {
        val listed = inspector.subfolders(project.dir)
        // A listing that ignores cancellation must not replace a dialog opened after "Anuluj".
        currentCoroutineContext().ensureActive()
        when (listed) {
            is Outcome.Err -> {
                logger.warn("project subfolders listing failed", "reason" to listed.error.category())
                ui.update { it.copy(dialog = Dialog.Message(ProjectMessages.of(listed.error))) }
            }

            is Outcome.Ok -> {
                ui.update { it.copy(dialog = dialogOf(project, listed.value, it.projects)) }
            }
        }
    }

    private fun dialogOf(
        project: Project,
        onDisk: List<SubfolderName>,
        all: List<Project>,
    ): Dialog.Subfolders {
        val checked = project.subfolders.toSet()
        val own = all.filter { it.id != project.id }.map { it.dir }.toSet()
        val choices =
            (onDisk + checked).distinct().map { name ->
                val state =
                    when {
                        name !in onDisk -> ChoiceState.MISSING
                        name !in checked && ProjectGroups.dirOf(project, name) in own -> ChoiceState.OWN_PROJECT
                        else -> ChoiceState.AVAILABLE
                    }
                SubfolderChoice(name, state)
            }
        return Dialog.Subfolders(project, choices.sortedWith(compareBy(ProjectGroups.order) { it.name }), checked)
    }
}
