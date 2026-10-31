package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import java.nio.file.Path

/**
 * [ProjectInspector] that reports [world] for every existing directory. Passes
 * [ProjectInspectorContract].
 *
 * [realPath] resolves the normalized path (default: unchanged; null = nothing there) and [pathKind]
 * decides what is at the real path (default [PathKind.DIRECTORY], no disk access), like in
 * [FakeProjectsApi]. [DirectoryReport.alreadyAdded] comes from [projects] when given, else from
 * [InspectionWorld.registered].
 */
public class FakeProjectInspector(
    private val world: InspectionWorld = InspectionWorld(),
    private val projects: ProjectsApi? = null,
    private val pathKind: (Path) -> PathKind = { PathKind.DIRECTORY },
    private val realPath: (Path) -> Path? = { it },
) : ProjectInspector {
    override suspend fun inspect(dir: Path): Outcome<DirectoryReport, ProjectError> =
        ProjectInputRules.normalizeDir(dir).flatMap { normalized ->
            val real = realPath(normalized)
            when (real?.let(pathKind)) {
                PathKind.DIRECTORY -> Outcome.Ok(report(real))
                PathKind.FILE -> Outcome.Err(ProjectError.NotADirectory(normalized))
                PathKind.MISSING, null -> Outcome.Err(ProjectError.NotFound(normalized))
            }
        }

    private fun report(dir: Path): DirectoryReport =
        DirectoryReport(
            dir = dir,
            git = world.git,
            hasClaudeMd = world.claudeMd,
            sessionCount = world.sessions.takeIf { world.runtimeAvailable },
            hooks = world.hooks,
            alreadyAdded = registered().firstOrNull { it.dir == dir }?.id,
        )

    private fun registered(): List<Project> = projects?.projects?.value ?: world.registered
}
