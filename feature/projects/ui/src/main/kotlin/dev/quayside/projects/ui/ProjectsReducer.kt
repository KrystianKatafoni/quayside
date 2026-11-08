package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.SessionCounts

/** Window-menu commands are enabled only without an open dialog, so ⌘⌫ reaches text fields in dialogs. */
internal fun ProjectsUiState.menu(): ProjectsMenuState =
    ProjectsMenuState(
        canAdd = dialog == null,
        canRemove = dialog == null && selected != null,
        canRename = dialog == null && selected != null,
        dialogOpen = dialog != null,
        canEditSubfolders = dialog == null && selected != null,
    )

/**
 * The new [list]; a selected project that left it is no longer selected, and a selected subfolder that left the
 * group of its project leaves the focus on the project. A group that is not one any more is no longer collapsed.
 */
internal fun ProjectsUiState.withProjects(list: List<Project>): ProjectsUiState {
    val project = list.find { it.id == selected }
    return copy(
        projects = list,
        selected = project?.id,
        subfolder = subfolder?.takeIf { project != null && it in project.subfolders },
        collapsed = collapsed.filterTo(mutableSetOf()) { id -> list.any { it.id == id && it.subfolders.isNotEmpty() } },
    )
}

/** Counts are trusted only from a fresh snapshot; otherwise [Dialog.ConfirmRemove.active] is null. */
internal fun ProjectsUiState.confirmRemove(project: Project): Dialog.ConfirmRemove =
    Dialog.ConfirmRemove(
        project,
        active = if (sessions.fresh) sessions.counts[project.id] ?: SessionCounts(0, 0) else null,
    )

internal fun ProjectsUiState.selectedProject(): Project? = selected?.let { id -> projects.find { it.id == id } }

/** The synchronous events: they only change the state, so they need no coroutine. Others are ignored. */
internal fun ProjectsUiState.reduce(event: SidebarEvent): ProjectsUiState =
    when {
        event == SidebarEvent.DismissBanner -> {
            copy(banner = null)
        }

        event is SidebarEvent.RenameRequested -> {
            projects.find { it.id == event.id }.let { openRename(it) }
        }

        event is SidebarEvent.NameChanged && dialog is Dialog.Rename -> {
            copy(dialog = dialog.copy(draft = event.text, error = null))
        }

        else -> {
            reduceSelection(event)
        }
    }

/** Row clicks, the chevron and the lines of the subfolders dialog. */
private fun ProjectsUiState.reduceSelection(event: SidebarEvent): ProjectsUiState =
    when (event) {
        is SidebarEvent.Select -> if (dialog == null) select(event) else this
        is SidebarEvent.ToggleGroup -> if (dialog == null) toggleGroup(event.id) else this
        is SidebarEvent.ToggleSubfolder -> toggleSubfolder(event.name)
        else -> this
    }

/** "Rename…" opens the dialog only for a listed project and only when no dialog is open. */
private fun ProjectsUiState.openRename(project: Project?): ProjectsUiState =
    if (dialog == null && project != null) copy(dialog = Dialog.Rename(project, project.displayName)) else this

/** A row click: the project row clears the subfolder; a subfolder row needs a name from the group of its project. */
private fun ProjectsUiState.select(event: SidebarEvent.Select): ProjectsUiState {
    val name = event.subfolder ?: return copy(selected = event.id, subfolder = null)
    val project = projects.find { it.id == event.id }
    return if (project != null && name in project.subfolders) copy(selected = event.id, subfolder = name) else this
}

/** Hides or shows the subfolder rows; hiding them leaves a selected subfolder of that group on its project. */
private fun ProjectsUiState.toggleGroup(id: ProjectId): ProjectsUiState {
    if (projects.none { it.id == id && it.subfolders.isNotEmpty() }) return this
    val hiding = id !in collapsed
    return copy(
        collapsed = if (hiding) collapsed + id else collapsed - id,
        subfolder = if (hiding && selected == id) null else subfolder,
    )
}

/** Checks or unchecks a line of the open subfolders dialog; a line of a project of its own stays unchecked. */
private fun ProjectsUiState.toggleSubfolder(name: SubfolderName): ProjectsUiState {
    val open = dialog as? Dialog.Subfolders
    val choice = open?.choices?.find { it.name == name }
    if (open == null || choice == null || choice.state == ChoiceState.OWN_PROJECT) return this
    val checked = if (name in open.checked) open.checked - name else open.checked + name
    return copy(dialog = open.copy(checked = checked, error = null))
}
