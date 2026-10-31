package dev.quayside.projects.ui

import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionCounts

/** Window-menu commands are enabled only without an open dialog, so ⌘⌫ reaches text fields in dialogs. */
internal fun ProjectsUiState.menu(): ProjectsMenuState =
    ProjectsMenuState(
        canAdd = dialog == null,
        canRemove = dialog == null && selected != null,
        canRename = dialog == null && selected != null,
        dialogOpen = dialog != null,
    )

/** The new [list]; a selected project that left it is no longer selected. */
internal fun ProjectsUiState.withProjects(list: List<Project>): ProjectsUiState =
    copy(projects = list, selected = selected?.takeIf { id -> list.any { it.id == id } })

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
        event is SidebarEvent.Select -> {
            if (dialog == null) copy(selected = event.id) else this
        }

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
            this
        }
    }

/** "Rename…" opens the dialog only for a listed project and only when no dialog is open. */
private fun ProjectsUiState.openRename(project: Project?): ProjectsUiState =
    if (dialog == null && project != null) copy(dialog = Dialog.Rename(project, project.displayName)) else this
