package dev.quayside.core.process

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * The checked-out git branch of a directory, read from `HEAD` without running git (cheap enough to poll). Blocking
 * file IO: call it on an IO dispatcher.
 */
public object GitBranch {
    private const val REF_PREFIX = "ref: refs/heads/"
    private const val GITDIR_PREFIX = "gitdir:"
    private const val SHORT_SHA = 7
    private const val MAX_HEAD_BYTES = 4096L
    private val SHA = Regex("[0-9a-f]{40,64}")

    /**
     * The branch of the work tree that contains [dir] (a parent holds the `.git`), or the first 7 characters of the
     * commit for a detached `HEAD`; null when no repository contains [dir] or `HEAD` cannot be read. Follows the
     * `.git` file of a linked worktree. Never throws; reads at most 4 KiB of `HEAD`.
     */
    public fun read(dir: Path): String? {
        val head = generateSequence(dir.toAbsolutePath().normalize()) { it.parent }.firstNotNullOfOrNull(::headOf)
        return head?.let(::branchIn)
    }

    private fun headOf(candidate: Path): Path? {
        val git = candidate.resolve(".git")
        val gitDir =
            when {
                git.isDirectory() -> git
                git.isRegularFile() -> linkedDir(candidate, git)
                else -> null
            }
        return gitDir?.resolve("HEAD")?.takeIf { it.isRegularFile() }
    }

    private fun linkedDir(
        base: Path,
        file: Path,
    ): Path? =
        text(file)
            ?.lineSequence()
            ?.firstOrNull { it.startsWith(GITDIR_PREFIX) }
            ?.removePrefix(GITDIR_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { base.resolve(it).normalize() }.getOrNull() }

    private fun branchIn(head: Path): String? {
        val line =
            text(head)
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
        return when {
            line.startsWith(REF_PREFIX) -> line.removePrefix(REF_PREFIX).takeIf { it.isNotEmpty() }
            SHA.matches(line) -> line.take(SHORT_SHA)
            else -> null
        }
    }

    private fun text(file: Path): String? =
        try {
            if (Files.size(file) > MAX_HEAD_BYTES) null else Files.readString(file)
        } catch (ignored: IOException) {
            null
        } catch (ignored: RuntimeException) {
            // A path that cannot be read (e.g. a malformed name) is "no repository", never a crash.
            null
        }
}
