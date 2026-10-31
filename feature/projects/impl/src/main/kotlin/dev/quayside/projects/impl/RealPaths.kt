package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.flatMap
import dev.quayside.projects.api.ProjectError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Adapter for resolving a directory to its real path on disk. */
internal object RealPaths {
    /**
     * Real path (`toRealPath()`) of the normalized [dir], on [io]. Anything that prevents resolving it
     * (missing, broken symlink, loop, no permission) → [ProjectError.NotFound]; not a directory →
     * [ProjectError.NotADirectory]. Errors carry [dir] as given; exception messages are dropped (ENG-C8).
     */
    suspend fun resolve(
        dir: Path,
        io: CoroutineDispatcher,
    ): Outcome<Path, ProjectError> =
        withContext(io) {
            catching<Path, ProjectError>({ ProjectError.NotFound(dir) }) { dir.toRealPath() }
                .flatMap { real ->
                    if (Files.isDirectory(real)) Outcome.Ok(real) else Outcome.Err(ProjectError.NotADirectory(dir))
                }
        }
}
