package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.info
import dev.agentmanager.core.domain.warn
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.sessions.api.SessionCommands
import dev.agentmanager.sessions.api.SessionError

/**
 * The confirmed commands behind the dialogs: log a fixed category and return the failure text, null = done.
 * Logs hold only ids, counts and categories, never a name or a path (ENG-C8).
 */
internal class ProjectCommands(
    private val projects: ProjectsApi,
    private val sessions: SessionCommands,
    private val logger: Logger,
) {
    /** Renames project [id] to [draft] (validated by `projects`). */
    suspend fun rename(
        id: ProjectId,
        draft: String,
    ): UiText? =
        when (val renamed = projects.rename(id, draft)) {
            is Outcome.Ok -> {
                logger.info("project renamed", "projectId" to id.value)
                null
            }

            is Outcome.Err -> {
                logger.warn("project rename failed", "projectId" to id.value, "reason" to renamed.error.category())
                ProjectMessages.of(renamed.error)
            }
        }

    /** Optionally stops every session of [project] in one command, then removes it; a failed stop keeps it listed. */
    suspend fun remove(
        project: Project,
        stop: Boolean,
    ): UiText? {
        var stopped = 0
        if (stop) {
            when (val result = sessions.stopAllIn(project.id)) {
                is Outcome.Ok -> stopped = result.value
                is Outcome.Err -> return stopFailed(project, result.error)
            }
        }
        return when (val removed = projects.remove(project.id)) {
            is Outcome.Ok -> {
                logger.info("project removed", "projectId" to project.id.value, "sessionsStopped" to stopped)
                null
            }

            is Outcome.Err -> {
                logger.warn("project remove failed", "reason" to removed.error.category())
                ProjectMessages.of(removed.error)
            }
        }
    }

    private fun stopFailed(
        project: Project,
        error: SessionError,
    ): UiText {
        val fields =
            buildMap<String, Any?> {
                put("projectId", project.id.value)
                put("reason", StopMessages.category(error))
                if (error is SessionError.StopIncomplete) put("failed", error.failed.size)
            }
        logger.log(LogLevel.WARN, "session stop failed", fields)
        return StopMessages.of(error)
    }
}
