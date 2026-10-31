package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * Registered projects (FR-P1, FR-P3, FR-P6). Never touches project files: it only keeps the list.
 *
 * Every `suspend` function may be called from any dispatcher, never blocks the calling thread and
 * never throws except cancellation (ENG-I4, ENG-I5). Commands are atomic: after any `Err`, including
 * [ProjectError.StorageFailed], [projects] and the stored list are unchanged.
 *
 * Start-up: a missing list file means an empty list. A list file that cannot be parsed is moved
 * aside by the store and the list starts empty; commands work normally. A list file that cannot be
 * read, or was written by a newer app version, also gives an empty [projects], but then every
 * command returns [ProjectError.StorageFailed] ([StorageProblem.READ_FAILED] or
 * [StorageProblem.NEWER_SCHEMA]) and the file is never overwritten. [storageIssue] tells the UI
 * which of these happened.
 */
public interface ProjectsApi {
    /**
     * Registered projects in the order in which they were added. Updated before a command
     * returns, so `projects.value` already reflects a successful [add], [remove] or [rename].
     */
    public val projects: StateFlow<List<Project>>

    /**
     * What went wrong with the list file at start-up, null = nothing. A fixed category without the
     * path (ENG-C8), so the UI can tell an empty list from a list that could not be loaded. Set
     * before [projects] shows the loaded list and does not change afterwards; dismissing the message
     * is UI state.
     */
    public val storageIssue: StateFlow<StorageIssue?>

    /**
     * Registers the existing directory [dir] and stores its real path as [Project.dir]: [dir] is
     * normalized with [ProjectInputRules.normalizeDir], then resolved with `toRealPath()`. Two paths to
     * the same directory (`a/./b`, `a/b/../b`, a symlink to it or under a symlinked parent) are the
     * same project. A path that cannot be resolved (missing, a broken symlink, a symlink loop, no
     * permission) is [ProjectError.NotFound].
     *
     * [displayName] null = a name derived from the directory, which never fails (see
     * [ProjectInputRules.displayName]); a given name is trimmed and validated. Concurrent calls for the
     * same directory register it exactly once; the others return [ProjectError.AlreadyAdded].
     *
     * Errors: [ProjectError.NotAbsolute], [ProjectError.NotFound], [ProjectError.NotADirectory],
     * [ProjectError.AlreadyAdded], [ProjectError.StorageFailed], and [ProjectError.InvalidDisplayName]
     * only when [displayName] is not null.
     */
    public suspend fun add(
        dir: Path,
        displayName: String? = null,
    ): Outcome<Project, ProjectError>

    /**
     * Removes the project from the list only; files, transcripts and worktrees stay on disk.
     * Running sessions are not touched.
     *
     * Errors: [ProjectError.UnknownProject], [ProjectError.StorageFailed].
     */
    public suspend fun remove(id: ProjectId): Outcome<Unit, ProjectError>

    /**
     * Changes only the display name; keeps id, dir and position in the list. [displayName] is
     * trimmed and validated like an explicit name in [add].
     *
     * Errors: [ProjectError.UnknownProject], [ProjectError.InvalidDisplayName],
     * [ProjectError.StorageFailed].
     */
    public suspend fun rename(
        id: ProjectId,
        displayName: String,
    ): Outcome<Unit, ProjectError>
}

/**
 * A problem with the project list file found at start-up and reported by [ProjectsApi.storageIssue].
 * Mirrors what `core:storage` `JsonStore` reports when loading.
 */
public enum class StorageIssue {
    /**
     * The file could not be parsed. It was moved aside as `projects.json.broken`, the list starts
     * empty and commands work normally.
     */
    RECOVERED_FROM_BROKEN_FILE,

    /**
     * The file exists but could not be read. The list is empty, every command returns
     * [ProjectError.StorageFailed] with [StorageProblem.READ_FAILED] and the file is never
     * overwritten.
     */
    READ_FAILED,

    /**
     * The file was written by a newer app version. The list is empty, every command returns
     * [ProjectError.StorageFailed] with [StorageProblem.NEWER_SCHEMA] and the file is never
     * overwritten.
     */
    NEWER_SCHEMA,
}
