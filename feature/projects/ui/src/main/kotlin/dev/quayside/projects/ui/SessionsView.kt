package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus

/**
 * Per-project counts for the sidebar; [fresh] = from a successful listing (the remove dialog trusts only that).
 * [subfolders] counts the active sessions of the project (not of a nested project) whose directory is in the subfolder
 * `ProjectFocus(project, subfolder)`; a session in the project directory or in an unchecked subdirectory has no entry
 * there. Entries without active sessions are left out.
 */
internal data class SessionsView(
    val counts: Map<ProjectId, SessionCounts>,
    val fresh: Boolean,
    val subfolders: Map<ProjectFocus, SessionCounts> = emptyMap(),
) {
    companion object {
        /** Nothing known yet: no counts, not fresh. */
        val Unknown: SessionsView = SessionsView(emptyMap(), fresh = false)

        /** Loading gives [Unknown]; Fresh and Stale count the active sessions of every listed project. */
        fun of(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
        ): SessionsView =
            when (snapshot.status) {
                SnapshotStatus.Loading -> Unknown
                is SnapshotStatus.Fresh -> counted(projects, snapshot, fresh = true)
                is SnapshotStatus.Stale -> counted(projects, snapshot, fresh = false)
            }

        private fun counted(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
            fresh: Boolean,
        ): SessionsView = SessionsView(activeCounts(projects, snapshot), fresh, subfolderCounts(projects, snapshot))

        /** Only sessions the project owns (`Session.project`) count; the subfolder comes from their directory. */
        private fun subfolderCounts(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
        ): Map<ProjectFocus, SessionCounts> =
            projects
                .filter { it.subfolders.isNotEmpty() }
                .flatMap { project ->
                    snapshot.sessions
                        .filter { it.project == project.id && it.state.isActive }
                        .mapNotNull { session ->
                            ProjectGroups.subfolderOf(project, session.cwd)?.let {
                                ProjectFocus(project.id, it) to
                                    session
                            }
                        }
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, sessions) ->
                    SessionCounts(
                        working = sessions.count { it.state == SessionState.Working },
                        waiting = sessions.count { it.state.isWaiting },
                    )
                }

        private fun activeCounts(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
        ): Map<ProjectId, SessionCounts> =
            projects.associate { it.id to snapshot.countsFor(it.id) }.filterValues { it.active > 0 }
    }
}

/** Texts of the counters of a row: waiting first (needs the user), then working; zeros give no text. */
internal object CountBadges {
    /** Key of the highlighted badge; the sidebar picks its color by it. */
    const val WAITING_KEY = "row.waiting"

    fun of(counts: SessionCounts?): List<UiText> =
        listOfNotNull(
            counts?.waiting?.takeIf { it > 0 }?.let { UiText(WAITING_KEY, listOf(it.toString())) },
            counts?.working?.takeIf { it > 0 }?.let { UiText("row.working", listOf(it.toString())) },
        )
}

/** Fixed text and log category for a failed `stopAllIn`; never ids or paths (ENG-C8). */
internal object StopMessages {
    fun of(error: SessionError): UiText =
        when (error) {
            is SessionError.Runtime -> UiText("remove.runtimeFailed")

            is SessionError.StopIncomplete,
            is SessionError.UnknownProject,
            is SessionError.UnknownSession,
            is SessionError.SessionActive,
            is SessionError.SubfolderUnavailable,
            -> UiText("remove.stopFailed")
        }

    fun category(error: SessionError): String =
        when (error) {
            is SessionError.StopIncomplete -> "stopIncomplete"
            is SessionError.UnknownProject -> "unknownProject"
            is SessionError.UnknownSession -> "unknownSession"
            is SessionError.SessionActive -> "sessionActive"
            is SessionError.Runtime -> "runtime.${error.problem.name}"
            is SessionError.SubfolderUnavailable -> "subfolderUnavailable"
        }
}
