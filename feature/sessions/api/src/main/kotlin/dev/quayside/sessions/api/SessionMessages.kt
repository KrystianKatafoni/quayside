package dev.quayside.sessions.api

import java.time.Instant

/** Position in the session file of a disk session (bytes); opaque for callers (ADR 0034). */
public data class MessageCursor(
    val offset: Long,
)

/** @property older cursor for the previous page; null = the start of the file was reached. */
public data class SessionMessagePage(
    val messages: List<SessionMessage>,
    val older: MessageCursor?,
)

/**
 * A message of a conversation; [text] is user data: `toString` hides it (ENG-C8). [truncated]: cut at 4 000 code
 * points.
 */
public data class SessionMessage(
    val role: MessageRole,
    val text: String,
    val at: Instant?,
    val truncated: Boolean,
) {
    override fun toString(): String = "SessionMessage(role=$role, text=$HIDDEN, at=$at, truncated=$truncated)"
}

/** Who wrote a [SessionMessage]. */
public enum class MessageRole { USER, ASSISTANT }
