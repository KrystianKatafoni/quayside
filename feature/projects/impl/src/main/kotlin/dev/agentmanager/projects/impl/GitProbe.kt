package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.debug
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessResult
import dev.agentmanager.core.process.ProcessRunner
import dev.agentmanager.core.process.RunOptions
import dev.agentmanager.projects.api.GitInfo
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Adapter: the only place that runs git. Every call starts in [NEUTRAL_DIR] and names the inspected dir with
 * `git -C`, so a login shell whose PATH holds `.` or an empty entry cannot run a `git` planted in that dir.
 */
internal class GitProbe(
    private val process: ProcessRunner,
    private val executable: String,
    timeout: Duration,
    private val logger: Logger,
) {
    private val options = RunOptions(timeout = timeout, maxOutputBytes = MAX_OUTPUT_BYTES)

    /**
     * Git work tree info of [dir], or null when [dir] is not in a work tree or git cannot be run.
     * The branch is asked only inside a work tree; a failed branch call gives null, not a guess.
     * Logs fixed categories only, never [dir], the branch or git output (ENG-C8).
     */
    suspend fun info(dir: Path): GitInfo? {
        val workTree = run(GitCommands.isInsideWorkTree(executable, dir))
        if (workTree != null && !workTree.isSuccess) logger.debug(UNAVAILABLE, REASON to "git:exit")
        val inside = workTree != null && GitOutput.isWorkTree(workTree)
        return if (inside) run(GitCommands.branch(executable, dir))?.let { GitInfo(GitOutput.branch(it)) } else null
    }

    private suspend fun run(argv: List<String>): ProcessResult? =
        when (val result = process.run(ShellCommand(argv, workDir = NEUTRAL_DIR), options)) {
            is Outcome.Ok -> {
                result.value
            }

            is Outcome.Err -> {
                logger.debug(UNAVAILABLE, REASON to category(result.error))
                null
            }
        }

    private fun category(error: ProcessError): String =
        when (error) {
            is ProcessError.NotFound -> "process:not-found"
            is ProcessError.Timeout -> "process:timeout"
            is ProcessError.StartFailed -> "process:start-failed"
            is ProcessError.IoFailed -> "process:io-failed"
        }

    private companion object {
        const val MAX_OUTPUT_BYTES = 4096
        const val UNAVAILABLE = "inspect git unavailable"
        const val REASON = "reason"

        /** Root-owned, always present and unrelated to the inspected dir. */
        val NEUTRAL_DIR: Path = Path.of("/")
    }
}

/**
 * Pure argv builders for the git calls of [GitProbe]; `-C dir` names the inspected (absolute, real) dir.
 * `--no-optional-locks` keeps git from refreshing the index or taking locks; `core.fsmonitor=false` and
 * `core.hooksPath=/dev/null` keep a foreign repository from running its own commands.
 */
internal object GitCommands {
    private val SAFE = listOf("--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.hooksPath=/dev/null")

    fun isInsideWorkTree(
        exe: String,
        dir: Path,
    ): List<String> = base(exe, dir) + listOf("rev-parse", "--is-inside-work-tree")

    fun branch(
        exe: String,
        dir: Path,
    ): List<String> = base(exe, dir) + listOf("symbolic-ref", "--quiet", "--short", "HEAD")

    private fun base(
        exe: String,
        dir: Path,
    ): List<String> = listOf(exe, "-C", dir.toString()) + SAFE
}

/** Pure reading of git results. */
internal object GitOutput {
    /** `true` only for exit code 0 with `true` on stdout; `.git` and bare repositories say `false`. */
    fun isWorkTree(r: ProcessResult): Boolean = r.isSuccess && r.stdout.trim() == "true"

    /** The short branch name, or null for detached HEAD (exit code 1), an empty or a truncated answer. */
    fun branch(r: ProcessResult): String? =
        r.stdout.trim().takeIf { r.isSuccess && !r.stdoutTruncated && it.isNotEmpty() }
}
