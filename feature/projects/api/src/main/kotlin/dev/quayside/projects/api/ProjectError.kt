package dev.quayside.projects.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/**
 * Why a [ProjectsApi] or [ProjectInspector] operation failed.
 *
 * Paths are user data (they hold the user name): UI may show them, logs only at DEBUG (ENG-C8).
 * `toString` of every variant with a path prints `dir=***`, so an error that ends up in a log line
 * never leaks it; equality still compares the path. A path in an error is the one the caller gave,
 * after [ProjectInputRules.normalizeDir], not the resolved real path.
 */
public sealed interface ProjectError {
    /** [dir] is relative; only absolute paths can be registered or inspected. */
    public data class NotAbsolute(
        val dir: Path,
    ) : ProjectError {
        override fun toString(): String = "NotAbsolute(dir=$HIDDEN)"
    }

    /**
     * Nothing can be found at [dir]: it does not exist, is a broken symlink or a symlink loop, or
     * cannot be resolved for lack of permission.
     */
    public data class NotFound(
        val dir: Path,
    ) : ProjectError {
        override fun toString(): String = "NotFound(dir=$HIDDEN)"
    }

    /** [dir] exists but is not a directory, e.g. a regular file or a symlink to one. */
    public data class NotADirectory(
        val dir: Path,
    ) : ProjectError {
        override fun toString(): String = "NotADirectory(dir=$HIDDEN)"
    }

    /** The directory, compared by its real path, is already registered as [existing]. */
    public data class AlreadyAdded(
        val existing: ProjectId,
    ) : ProjectError

    /** No registered project has [id]. */
    public data class UnknownProject(
        val id: ProjectId,
    ) : ProjectError

    /** An explicit display name was rejected for [reason]. */
    public data class InvalidDisplayName(
        val reason: NameProblem,
    ) : ProjectError

    /** The project list could not be read or written, for [problem]. */
    public data class StorageFailed(
        val problem: StorageProblem,
    ) : ProjectError

    private companion object {
        const val HIDDEN = "***"
    }
}

/** Why a display name was rejected by [ProjectInputRules.displayName]. */
public enum class NameProblem {
    /** Empty or only whitespace. */
    BLANK,

    /** More than [ProjectInputRules.MAX_DISPLAY_NAME_LENGTH] code points after trimming. */
    TOO_LONG,

    /**
     * Contains an ISO control character (a new line, a tab, NUL), an invisible format character
     * (Unicode category Cf, e.g. the bidi override U+202E), a line or paragraph separator
     * (U+2028, U+2029) or a lone surrogate.
     */
    CONTROL_CHARACTERS,
}

/**
 * Why the project list could not be read or written; a fixed category, never an exception message
 * (ENG-C8). Mirrors the errors of `core:storage` `JsonStore`. A file that cannot be parsed is not an
 * error: the store moves it aside and starts from an empty list.
 */
public enum class StorageProblem {
    /** The list file exists but could not be read, e.g. missing permissions. */
    READ_FAILED,

    /** The new list could not be written, e.g. a full disk. */
    WRITE_FAILED,

    /** The list file was written by a newer app version; it is never overwritten. */
    NEWER_SCHEMA,
}
