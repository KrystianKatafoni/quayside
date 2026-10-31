package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.catching
import dev.agentmanager.core.domain.getOrElse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Detection of `CLAUDE.md` in a directory. */
internal object ClaudeMd {
    private val CANDIDATES = listOf("CLAUDE.md", ".claude/CLAUDE.md")

    /**
     * Whether [dir] has a regular file (symlinks followed) `CLAUDE.md` or `.claude/CLAUDE.md`, checked on
     * [io]. A directory with that name does not count; a denied check counts as absent.
     */
    suspend fun present(
        dir: Path,
        io: CoroutineDispatcher,
    ): Boolean =
        withContext(io) {
            catching<Boolean, Unit>({}) { CANDIDATES.any { Files.isRegularFile(dir.resolve(it)) } }.getOrElse { false }
        }
}
