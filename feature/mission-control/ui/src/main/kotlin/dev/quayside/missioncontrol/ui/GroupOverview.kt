package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.Session

/** One subfolder row: [stats] count the sessions of the group whose directory is in [name] (FR-P8). */
internal data class SubfolderStats(
    val name: SubfolderName,
    val stats: SessionStats,
)

/**
 * The overview of group [project]: one row per subfolder in display order. Sessions outside every subfolder (the
 * project directory and unchecked subdirectories) have no row.
 */
internal data class GroupOverview(
    val project: ProjectId,
    val rows: List<SubfolderStats>,
)

internal object GroupOverviews {
    /**
     * Null when [project] has no subfolders. Counts only sessions with `project == project.id` (a nested project
     * keeps its own); tiers: waiting > 0, failed > 0, working > 0, the rest; inside a tier [ProjectGroups.order].
     */
    fun of(
        project: Project,
        sessions: List<Session>,
    ): GroupOverview? {
        if (project.subfolders.isEmpty()) return null
        val bySubfolder =
            sessions.filter { it.project == project.id }.groupBy {
                ProjectGroups.subfolderOf(
                    project,
                    it.cwd,
                )
            }
        val rows =
            project.subfolders
                .map { SubfolderStats(it, statsOf(bySubfolder[it])) }
                .sortedWith(compareBy<SubfolderStats> { tier(it.stats) }.thenBy(ProjectGroups.order) { it.name })
        return GroupOverview(project.id, rows)
    }

    private fun statsOf(sessions: List<Session>?): SessionStats = SessionStats.of(sessions.orEmpty().map { it.state })

    private fun tier(stats: SessionStats): Int =
        when {
            stats.waiting > 0 -> 0
            stats.failed > 0 -> 1
            stats.working > 0 -> 2
            else -> QUIET
        }

    private const val QUIET = 3
}
