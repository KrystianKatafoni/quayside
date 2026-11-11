package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path
import java.time.Instant

/**
 * Read-only access to Claude Code session files (ADR 0032). Any dispatcher; never blocks the calling thread, never
 * throws except cancellation; never writes, moves or deletes a file.
 */
public interface SessionHistory {
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

    /**
     * Up to [limit] (coerced to 1..100) messages of [conversation] that lie before [before] (null = end of file),
     * oldest first; the file is `<dirNameOf(startCwd)>/<uuid>.jsonl`, read backwards in chunks, never whole (ADR 0034).
     * Only `text` parts of user and assistant messages. Errors: [ConversationError]; an id that is not a lowercase
     * UUID is [ConversationError.NOT_FOUND] without touching the disk, and so is a `startCwd` other than the start
     * directory of the session.
     */
    public suspend fun conversation(
        conversation: ConversationId,
        startCwd: Path,
        before: HistoryCursor?,
        limit: Int,
    ): Outcome<ConversationPage, ConversationError>
}

/**
 * One session file. [title] (first user message, at most 80 code points), [name] and [lastPrompt] are user data:
 * `toString` hides all three (ENG-C8).
 *
 * @property conversationId UUID = file name stem.
 * @property startCwd first absolute `cwd` of the file.
 * @property startedAt first timestamp in the head; null when none.
 * @property lastActivityAt last timestamp in the tail; null when none.
 * @property modifiedAt modification time of the file.
 * @property cwdChanged more than one distinct `cwd` seen (lower bound: the middle of the file is not read).
 * @property name `customTitle` of the last `custom-title` entry; at most 80 code points; user data.
 * @property lastPrompt the last real user message of the tail; at most 80 code points; user data.
 */
public data class PastConversation(
    val conversationId: ConversationId,
    val startCwd: Path,
    val startedAt: Instant?,
    val lastActivityAt: Instant?,
    val modifiedAt: Instant,
    val cwdChanged: Boolean,
    val title: String?,
    val name: String? = null,
    val lastPrompt: String? = null,
) {
    /** Without [title], [name] and [lastPrompt]: user data (ENG-C8) that must not reach logs through templates. */
    override fun toString(): String =
        "PastConversation(conversationId=$conversationId, startCwd=$startCwd, startedAt=$startedAt, " +
            "lastActivityAt=$lastActivityAt, modifiedAt=$modifiedAt, cwdChanged=$cwdChanged, title=<redacted>)"
}

/** Why the listing failed as a whole; a single bad file is skipped, not an error. Fixed categories, no paths. */
public enum class HistoryError {
    /** The `projects` directory exists but cannot be listed. */
    UNREADABLE,
}

/**
 * A byte position in the session file: usually the start of the line of a message, but after a read that stopped
 * inside a very long line, a position in the middle of it (the next read skips that partial line). Opaque for
 * callers, valid while the file is only appended to.
 */
public data class HistoryCursor(
    val offset: Long,
)

/** @property older cursor for the previous page; null = the start of the file was reached. */
public data class ConversationPage(
    val messages: List<ConversationMessage>,
    val older: HistoryCursor?,
)

/** [text] is user data: `toString` hides it. [truncated]: cut to 4 000 code points. */
public data class ConversationMessage(
    val role: ConversationRole,
    val text: String,
    val at: Instant?,
    val truncated: Boolean,
) {
    override fun toString(): String = "ConversationMessage(role=$role, text=<redacted>, at=$at, truncated=$truncated)"
}

/** Who wrote a [ConversationMessage]. */
public enum class ConversationRole { USER, ASSISTANT }

/** Why [SessionHistory.conversation] failed. Fixed categories, no paths. */
public enum class ConversationError {
    /** No such file or invalid id. */
    NOT_FOUND,

    /** IO failure or cursor past the end. */
    UNREADABLE,
}
