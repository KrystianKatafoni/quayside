package dev.quayside.sessions.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import java.nio.file.Path

/** Where a start runs (AM-308). [isDirectory] = `Files.isDirectory(p, NOFOLLOW_LINKS)` on `io`. */
internal class StartDirs(
    private val projects: ProjectsApi,
    private val isDirectory: suspend (Path) -> Boolean,
) {
    fun has(project: ProjectId): Boolean = projects.projects.value.any { it.id == project }

    /**
     * Project dir for a null [subfolder]; else checks that it is exactly (letter case included) one of
     * `Project.subfolders` ([SubfolderProblem.NOT_IN_PROJECT]), then that its directory is on the disk
     * ([SubfolderProblem.MISSING]). The root dir is never checked. Errors never carry the subfolder name.
     */
    suspend fun resolve(
        project: ProjectId,
        subfolder: String?,
    ): Outcome<Path, SessionError> {
        val found =
            projects.projects.value.firstOrNull { it.id == project }
                ?: return Outcome.Err(SessionError.UnknownProject(project))
        return if (subfolder == null) Outcome.Ok(found.dir) else subfolderDir(found, subfolder)
    }

    private suspend fun subfolderDir(
        project: Project,
        subfolder: String,
    ): Outcome<Path, SessionError> {
        val name = project.subfolders.firstOrNull { it.value == subfolder }
        val dir = name?.let { ProjectGroups.dirOf(project, it) }
        val problem =
            when {
                dir == null || !isDirectChild(project, dir) -> SubfolderProblem.NOT_IN_PROJECT
                !isDirectory(dir) -> SubfolderProblem.MISSING
                else -> return Outcome.Ok(dir)
            }
        return Outcome.Err(SessionError.SubfolderUnavailable(project.id, problem))
    }

    /** Second guard (no disk access): a listed name such as `..` or `a/b` must not leave the project dir. */
    private fun isDirectChild(
        project: Project,
        dir: Path,
    ): Boolean = dir.normalize().parent == project.dir.normalize()
}
