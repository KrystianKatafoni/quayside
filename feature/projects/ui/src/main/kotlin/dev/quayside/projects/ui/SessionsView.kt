package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus

/** Per-project counts for the sidebar; [fresh] = from a successful listing (the remove dialog trusts only that). */
internal data class SessionsView(
    val counts: Map<ProjectId, SessionCounts>,
    val fresh: Boolean,
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
                is SnapshotStatus.Fresh -> SessionsView(activeCounts(projects, snapshot), fresh = true)
                is SnapshotStatus.Stale -> SessionsView(activeCounts(projects, snapshot), fresh = false)
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
            -> UiText("remove.stopFailed")
        }

    fun category(error: SessionError): String =
        when (error) {
            is SessionError.StopIncomplete -> "stopIncomplete"
            is SessionError.UnknownProject -> "unknownProject"
            is SessionError.UnknownSession -> "unknownSession"
            is SessionError.SessionActive -> "sessionActive"
            is SessionError.Runtime -> "runtime.${error.problem.name}"
        }
}
