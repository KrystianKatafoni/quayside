package dev.quayside.projects.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/**
 * A directory registered in Quayside.
 *
 * [dir] is the canonical form of the directory: its real path (`Path.toRealPath()`), absolute, with
 * `.`/`..` removed and every symlink resolved, so it compares by components with the working
 * directory of a Claude Code session (e.g. `/private/tmp/x`, not `/tmp/x`, on macOS). Two paths to
 * the same directory, also through a symlink, are the same project.
 *
 * [dir] and [displayName] are user data: log them only at DEBUG and use [id] as the structured log
 * field (ENG-C8).
 *
 * @property id opaque identifier assigned by the implementation, stable between runs.
 * @property dir real path of the project directory, resolved when the project was added.
 * @property displayName name shown in the sidebar, trimmed, not blank, without control characters and
 * at most [ProjectInputRules.MAX_DISPLAY_NAME_LENGTH] code points.
 */
public data class Project(
    val id: ProjectId,
    val dir: Path,
    val displayName: String,
)
