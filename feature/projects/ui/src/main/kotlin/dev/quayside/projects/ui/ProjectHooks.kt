package dev.quayside.projects.ui

import dev.quayside.core.domain.Outcome
import java.nio.file.Path

/**
 * Quayside hooks in a project's `.claude/settings.local.json`, supplied by `app` (AM-610). The remove dialog
 * reads them to warn that removing the project from the list leaves them in the file (FR-P3).
 * Both functions may be called from any dispatcher, never block the calling thread and never throw except
 * cancellation. They never create [dir] or its `.claude` folder.
 */
public interface ProjectHooks {
    /** Our hooks in the local settings of [dir]; [ProjectHooksState.ABSENT] when [dir] is not a directory. */
    public suspend fun status(dir: Path): ProjectHooksState

    /** Removes only our hooks from the local settings of [dir]; other settings stay. Never restores a backup. */
    public suspend fun uninstall(dir: Path): Outcome<Unit, ProjectHooksError>
}

/** What the remove dialog shows about the hooks of a project. */
public enum class ProjectHooksState {
    /** No hooks of ours, no file, or the project folder is gone: nothing to warn about. */
    ABSENT,

    /** All or some of our hooks are in the file: warn and offer "Odinstaluj hooki". */
    PRESENT,

    /** The file could not be read: say so, offer nothing. */
    UNKNOWN,
}

/** Why [ProjectHooks.uninstall] failed; a fixed category, never a path or file content (ENG-C8). */
public enum class ProjectHooksError { UNREADABLE, WRITE_FAILED }

/** What `app` supplies to [ProjectsController] besides the `api` modules: keeps the constructor at 6 (ENG-C2). */
public class ProjectsPorts(
    public val picker: DirectoryPicker,
    public val hooks: ProjectHooks,
    /** The disk sessions of a new project (FR-P9); null = the add flow ends with the project (no wait, no choices). */
    public val past: PastSessionsPorts? = null,
)
