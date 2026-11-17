package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/** The session of a world entry as [FakeSessions] shows it; the project is the deepest of [projectDirs]. */
internal fun WorldSession.toSession(projectDirs: Map<ProjectId, Path>): Session =
    Session(
        id = id,
        project = SessionRules.projectFor(cwd, projectDirs),
        name = name,
        cwd = cwd,
        state = state,
        startedAt = startedAt,
        lastActivityAt = startedAt,
        summary = null,
        meta = meta,
        account = account,
    )

/** [sessions] in the order of a snapshot: by start time, then by id. */
internal fun ordered(sessions: List<Session>): List<Session> =
    sessions.sortedWith(compareBy<Session>({ it.startedAt }, { it.id.value }))

/** The sessions of a world in snapshot order. */
internal fun List<WorldSession>.toSessions(projectDirs: Map<ProjectId, Path>): List<Session> =
    ordered(map { it.toSession(projectDirs) })
