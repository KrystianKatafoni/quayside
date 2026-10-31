package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/**
 * Read-only check of a directory before adding it (FR-P2).
 *
 * Never modifies the directory, not even git metadata: an implementation runs git with
 * `--no-optional-locks` (no index refresh, no lock files) and only reads settings files.
 *
 * May be called from any dispatcher, never blocks the calling thread and never throws except
 * cancellation (ENG-I4, ENG-I5).
 */
public interface ProjectInspector {
    /**
     * Describes [dir]. [dir] is normalized with [ProjectInputRules.normalizeDir] and resolved to its
     * real path (`toRealPath()`), exactly like [ProjectsApi.add], so [DirectoryReport.dir] equals the
     * [Project.dir] the directory would get. A path that cannot be resolved (missing, a broken
     * symlink, a symlink loop, no permission) is [ProjectError.NotFound].
     *
     * Errors: [ProjectError.NotAbsolute], [ProjectError.NotFound], [ProjectError.NotADirectory].
     * Problems with git, the runtime or hooks land in the report, not as `Err`.
     */
    public suspend fun inspect(dir: Path): Outcome<DirectoryReport, ProjectError>
}

/**
 * What [ProjectInspector.inspect] found in a directory.
 *
 * [dir] is user data (ENG-C8): log it only at DEBUG.
 *
 * @property dir real path of the inspected directory, symlinks resolved (see [Project.dir]).
 * @property git non-null when [dir] is inside a git work tree: the root of a repository, any of its
 * subdirectories, or a linked worktree whose `.git` is a file. null = not a git repository or git
 * could not be run (missing binary, error); the two are not distinguished.
 * @property hasClaudeMd whether [dir] has `CLAUDE.md` or `.claude/CLAUDE.md`; parent directories and
 * `~/.claude` are not checked.
 * @property sessionCount active Claude Code sessions whose working directory is [dir] or below it,
 * with the semantics of the runtime's `listSessions(under = dir)`; ended sessions are not counted.
 * null = the runtime is unavailable.
 * @property hooks presence of Quayside hooks in [dir]'s `.claude/settings.json` and
 * `.claude/settings.local.json`, see [HooksPresence].
 * @property alreadyAdded id of the registered project with this real path, null if none.
 */
public data class DirectoryReport(
    val dir: Path,
    val git: GitInfo?,
    val hasClaudeMd: Boolean,
    val sessionCount: Int?,
    val hooks: HooksPresence,
    val alreadyAdded: ProjectId?,
)

/** Git repository of an inspected directory. [branch] null = detached HEAD. */
public data class GitInfo(
    val branch: String?,
)

/**
 * Our hooks in the directory's `.claude/settings.json` and `.claude/settings.local.json`. The two
 * files are combined: a hook counts as present when it is in either file. Missing files count as
 * empty.
 */
public enum class HooksPresence {
    /** None of our hooks in either file, or neither file exists. */
    NONE,

    /** Some of our hooks are present, others are missing from both files. */
    PARTIAL,

    /** Every one of our hooks is present in at least one of the files. */
    INSTALLED,

    /** At least one of the files exists but cannot be read or parsed; takes precedence over the rest. */
    UNREADABLE,
}
