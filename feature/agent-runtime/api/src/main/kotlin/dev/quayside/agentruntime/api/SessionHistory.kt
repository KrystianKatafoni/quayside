package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path
import java.time.Instant

/**
 * Read-only access to Claude Code session files (ADR 0032). Any dispatcher; never blocks the calling thread, never
 * throws except cancellation; never writes, moves or deletes a file.
 */
public fun interface SessionHistory {
    /**
     * Sessions whose start directory (first absolute `cwd`, docs/compat.md M1-M2) is one of [dirs] or below, from
     * files modified at or after [since] (null = any age). Reads at most 64 KiB from the start and the end of a
     * file. Files that are not listable sessions are skipped. Newest [PastConversation.modifiedAt] first; a
     * conversation matching several of [dirs] is listed once. A missing `projects` directory is `Ok(empty)`.
     * Errors: [HistoryError].
     */
    public suspend fun list(
        dirs: List<Path>,
        since: Instant?,
    ): Outcome<List<PastConversation>, HistoryError>
}

/**
 * One session file. [title] (first user message, at most 80 code points) is user data: `toString` hides it (ENG-C8).
 *
 * @property conversationId UUID = file name stem.
 * @property startCwd first absolute `cwd` of the file.
 * @property startedAt first timestamp in the head; null when none.
 * @property lastActivityAt last timestamp in the tail; null when none.
 * @property modifiedAt modification time of the file.
 * @property cwdChanged more than one distinct `cwd` seen (lower bound: the middle of the file is not read).
 */
public data class PastConversation(
    val conversationId: ConversationId,
    val startCwd: Path,
    val startedAt: Instant?,
    val lastActivityAt: Instant?,
    val modifiedAt: Instant,
    val cwdChanged: Boolean,
    val title: String?,
) {
    /** Without [title]: it is user data (ENG-C8) and must not reach logs through string templates. */
    override fun toString(): String =
        "PastConversation(conversationId=$conversationId, startCwd=$startCwd, startedAt=$startedAt, " +
            "lastActivityAt=$lastActivityAt, modifiedAt=$modifiedAt, cwdChanged=$cwdChanged, title=<redacted>)"
}

/** Why the listing failed as a whole; a single bad file is skipped, not an error. Fixed categories, no paths. */
public enum class HistoryError {
    /** The `projects` directory exists but cannot be listed. */
    UNREADABLE,
}
