package dev.quayside.projects.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus

/**
 * Per-project counts for the sidebar; [fresh] = from a successful listing (the remove dialog trusts only that).
 * [counts] holds the active sessions of a project (the rail and the remove dialog); [statuses] the counters of
 * every sidebar row (only a project row draws them; a subfolder row uses them for its icon tone). Key
 * `ProjectFocus(id)` = every session with `project == id` (the whole group, like the session list);
 * `ProjectFocus(id, name)` = those whose directory is in that subfolder. A missing key = all zero.
 */
internal data class SessionsView(
    val counts: Map<ProjectId, SessionCounts>,
    val fresh: Boolean,
    val statuses: Map<ProjectFocus, StatusCounts> = emptyMap(),
) {
    companion object {
        /** Nothing known yet: no counts, not fresh. */
        val Unknown: SessionsView = SessionsView(emptyMap(), fresh = false)

        /** Loading gives [Unknown]; Fresh and Stale count the sessions of every listed project. */
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
        ): SessionsView = SessionsView(activeCounts(projects, snapshot), fresh, statusCounts(projects, snapshot))

        /**
         * Only sessions the project owns (`Session.project`) count; the subfolder comes from their directory. All-zero
         * entries are dropped (hygiene): tests compare views with `==` against the empty [SessionsView].
         */
        private fun statusCounts(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
        ): Map<ProjectFocus, StatusCounts> =
            projects
                .flatMap { project ->
                    val owned = snapshot.sessions.filter { it.project == project.id }
                    val bySubfolder = owned.groupBy { ProjectGroups.subfolderOf(project, it.cwd) }
                    listOf(ProjectFocus(project.id) to owned) +
                        bySubfolder.mapNotNull { (name, sessions) ->
                            name?.let { ProjectFocus(project.id, it) to sessions }
                        }
                }.associate { (focus, sessions) -> focus to StatusCounts.of(sessions.map { it.state }) }
                .filterValues { it != StatusCounts() }

        private fun activeCounts(
            projects: List<Project>,
            snapshot: SessionsSnapshot,
        ): Map<ProjectId, SessionCounts> =
            projects.associate { it.id to snapshot.countsFor(it.id) }.filterValues { it.active > 0 }
    }
}

/** One chip of a project row: its [text] (count and label), the [tone] and the [icon] of that state. */
internal data class CountBadge(
    val text: UiText,
    val tone: StatusTone,
    val icon: IconKind,
)

/** Chips of a project row: waiting (needs the user), working, failed; a state with no sessions has no chip. */
internal object CountBadges {
    fun of(counts: StatusCounts): List<CountBadge> =
        listOfNotNull(
            badge("row.waiting", counts.waiting, StatusTone.WARN, IconKind.WAITING),
            badge("row.working", counts.working, StatusTone.WORK, IconKind.WORKING),
            badge("row.failed", counts.failed, StatusTone.ERR, IconKind.FAILED),
        )

    private fun badge(
        key: String,
        count: Int,
        tone: StatusTone,
        icon: IconKind,
    ): CountBadge? = if (count == 0) null else CountBadge(UiText(key, listOf(count.toString())), tone, icon)
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
