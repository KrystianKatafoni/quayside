package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import java.nio.file.Path
import java.time.Instant

/** The scan of [FakeSessions]: session files of the world to disk sessions and per-project counts (FR-P9). */
internal object FakeDiskScan {
    private const val UUID_PREFIX = 8

    /**
     * Disk sessions and history for [files], by the rules of `sessions:impl`: files older than [since] or hidden in
     * [settings] are left out, so are conversations in [known] (the supervisor's ids, matched by their first 8
     * characters, or conversation UUIDs); the deepest of [projectDirs] owns a session; the newest per project up to
     * the count limit are kept.
     */
    fun scan(
        files: List<WorldPastSession>,
        known: List<SessionId>,
        projectDirs: Map<ProjectId, Path>,
        settings: SessionHistorySettings,
        since: Instant?,
    ): Pair<List<Session>, Map<ProjectId, ProjectHistory>> {
        val knownValues = known.map { it.value }.toSet()
        val byProject =
            files
                .filter { it.id !in settings.hidden && (since == null || it.modifiedAt >= since) }
                .filter { it.id.value !in knownValues && it.id.value.take(UUID_PREFIX) !in knownValues }
                .groupBy { SessionRules.projectFor(it.cwd, projectDirs) }
        val shown =
            projectDirs.keys.associateWith { project ->
                byProject[project].orEmpty().sortedByDescending { it.modifiedAt }
            }
        val kept = shown.mapValues { (_, list) -> list.take(settings.maxPerProject.count) }
        val history =
            shown.mapValues { (project, list) ->
                ProjectHistory(kept.getValue(project).size, list.size - kept.getValue(project).size)
            }
        return kept.flatMap { (project, list) -> list.map { toDiskSession(it, project) } } to history
    }

    private fun toDiskSession(
        file: WorldPastSession,
        project: ProjectId,
    ): Session =
        Session(
            id = file.id,
            project = project,
            name = file.title ?: file.id.value.take(UUID_PREFIX),
            cwd = file.cwd,
            state = SessionState.Stopped,
            startedAt = file.startedAt ?: file.modifiedAt,
            lastActivityAt = file.lastActivityAt ?: file.modifiedAt,
            summary = null,
            origin = SessionOrigin.DISK,
        )
}
