package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState

/** Global views of Mission Control over the sessions of every project, also those without a project (FR-S9). */
public enum class GlobalView {
    /** Sessions that wait for the user: [SessionState.isWaiting]. */
    WAITING,

    /** Sessions in [SessionState.Working]. */
    WORKING,

    /** Sessions in [SessionState.Done]. */
    DONE,

    /** Sessions in [SessionState.Failed]. */
    FAILED,
    ;

    internal fun matches(state: SessionState): Boolean =
        when (this) {
            WAITING -> state.isWaiting
            WORKING -> state == SessionState.Working
            DONE -> state == SessionState.Done
            FAILED -> state == SessionState.Failed
        }
}

/** Which sessions a [Selection] shows; the one place for the list, the cursor and the actions. */
internal object SessionScope {
    /**
     * Sessions of the shown global view, or of the selected project under the filter; newest `startedAt` first.
     * No view and no project show nothing. A selected subfolder narrows the project page to the sessions whose
     * directory is in that subfolder of its project in [projects]; a subfolder of an unlisted project shows nothing.
     */
    fun visible(
        sessions: List<Session>,
        selection: Selection,
        projects: List<Project>,
    ): List<Session> {
        val view = selection.view
        val project = selection.project
        val shown =
            when {
                view != null -> sessions.filter { view.matches(it.state) }
                project != null -> sessions.filter { inPage(it, selection, project, projects) }
                else -> emptyList()
            }
        return shown.sortedByDescending { it.startedAt }
    }
}

private fun inPage(
    session: Session,
    selection: Selection,
    project: ProjectId,
    projects: List<Project>,
): Boolean =
    session.project == project &&
        selection.filter.matches(session.state) &&
        session.isIn(projects.find { it.id == project }, selection.subfolder)

/** True without a [subfolder]; with one, when the session directory is in that subfolder of [project] (FR-P7). */
internal fun Session.isIn(
    project: Project?,
    subfolder: SubfolderName?,
): Boolean = subfolder == null || (project != null && ProjectGroups.subfolderOf(project, cwd) == subfolder)

/** The selection after the sidebar reports [id]: another project starts a fresh page, `null` keeps a global view. */
internal fun Selection.onProject(id: ProjectId?): Selection =
    when {
        id == project -> this
        id == null && view != null -> copy(project = null)
        else -> Selection(project = id)
    }

/**
 * The selection after the sidebar reports [focus]: another project or another subfolder of it starts a fresh page,
 * the same focus keeps everything (also a session selected by `SelectSession`, which already left the subfolder),
 * `null` as [onProject] with no project.
 */
internal fun Selection.onFocus(focus: ProjectFocus?): Selection {
    val subfolder = focus?.subfolder
    if (focus != null && focus.project == project && subfolder != this.subfolder) {
        return Selection(project = project, subfolder = subfolder)
    }
    return onProject(focus?.project).copy(subfolder = subfolder)
}

/** Title of the view as in the sidebar, e.g. "Czeka na mnie". */
public fun GlobalView.title(): String = MissionControlStrings["view.${name.lowercase()}"]
