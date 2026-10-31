package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionState

/** Global views of Mission Control over the sessions of every project, also those without a project (FR-S9). */
public enum class GlobalView {
    /** Sessions that wait for the user: [SessionState.isWaiting]. */
    WAITING,

    /** Sessions in [SessionState.Working]. */
    WORKING,

    /** Sessions in [SessionState.Failed]. */
    FAILED,
    ;

    internal fun matches(state: SessionState): Boolean =
        when (this) {
            WAITING -> state.isWaiting
            WORKING -> state == SessionState.Working
            FAILED -> state == SessionState.Failed
        }
}

/** Which sessions a [Selection] shows; the one place for the list, the cursor and the actions. */
internal object SessionScope {
    /**
     * Sessions of the shown global view, or of the selected project under the filter; newest `startedAt` first.
     * No view and no project show nothing.
     */
    fun visible(
        sessions: List<Session>,
        selection: Selection,
    ): List<Session> {
        val view = selection.view
        val project = selection.project
        val shown =
            when {
                view != null -> sessions.filter { view.matches(it.state) }
                project != null -> sessions.filter { it.project == project && selection.filter.matches(it.state) }
                else -> emptyList()
            }
        return shown.sortedByDescending { it.startedAt }
    }
}

/** The selection after the sidebar reports [id]: another project starts a fresh page, `null` keeps a global view. */
internal fun Selection.onProject(id: ProjectId?): Selection =
    when {
        id == project -> this
        id == null && view != null -> copy(project = null)
        else -> Selection(project = id)
    }
