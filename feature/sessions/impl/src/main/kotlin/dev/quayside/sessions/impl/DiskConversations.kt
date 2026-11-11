package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationPage
import dev.quayside.agentruntime.api.ConversationRole
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.warn
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.MessageRole
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMessage
import dev.quayside.sessions.api.SessionMessagePage
import dev.quayside.sessions.api.SessionOrigin

/**
 * The history of a disk session read from its session file (FR-P9, ADR 0034). Only a session that is in the
 * snapshot as [SessionOrigin.DISK] is read, so supervisor, hidden and unknown ids never reach the port; without a
 * `SessionHistory` there is none. Logs a fixed category only, never a message (ENG-C8).
 */
internal class DiskConversations(
    private val state: SessionsState,
    private val logger: Logger,
) {
    suspend fun page(
        id: SessionId,
        before: MessageCursor?,
    ): Outcome<SessionMessagePage, SessionError> {
        val disk =
            state.snapshot.value.sessions
                .firstOrNull { it.id == id && it.origin == SessionOrigin.DISK }
        val cursor = before?.let { HistoryCursor(it.offset) }
        val result = disk?.let { state.disk?.conversation(it, cursor, PAGE) }
        return when (result) {
            null -> Outcome.Err(SessionError.UnknownSession(id))
            is Outcome.Ok -> Outcome.Ok(result.value.toApi())
            is Outcome.Err -> Outcome.Err(failed(id, result.error))
        }
    }

    private fun failed(
        id: SessionId,
        error: ConversationError,
    ): SessionError =
        when (error) {
            ConversationError.NOT_FOUND -> {
                SessionError.UnknownSession(id)
            }

            ConversationError.UNREADABLE -> {
                logger.warn("Conversation read failed", "problem" to error.name)
                SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)
            }
        }

    private fun ConversationPage.toApi(): SessionMessagePage =
        SessionMessagePage(messages.map { it.toApi() }, older?.let { MessageCursor(it.offset) })

    private fun ConversationMessage.toApi(): SessionMessage =
        SessionMessage(
            role = if (role == ConversationRole.USER) MessageRole.USER else MessageRole.ASSISTANT,
            text = text,
            at = at,
            truncated = truncated,
        )

    private companion object {
        const val PAGE = 20
    }
}
