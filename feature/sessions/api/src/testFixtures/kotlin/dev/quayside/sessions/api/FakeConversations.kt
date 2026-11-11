package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/** The scripted session files of [FakeSessions]: messages by session, the reads made and one scripted failure. */
public class FakeConversations {
    private val messages = MutableStateFlow(emptyMap<SessionId, List<SessionMessage>>())
    private val recorded = MutableStateFlow(emptyList<Pair<SessionId, MessageCursor?>>())
    private val failing = MutableStateFlow(false)

    /** Reads of `conversation` so far, in order, also the failed ones: the session and the cursor. */
    public val reads: List<Pair<SessionId, MessageCursor?>> get() = recorded.value

    /** The file of [id] holds exactly [list] (oldest first); an empty list is a file without messages. */
    public fun set(
        id: SessionId,
        list: List<SessionMessage>,
    ) {
        messages.update { it + (id to list) }
    }

    /** The next read fails with `Runtime(COMMAND_FAILED)`; later reads work again. */
    public fun failNext() {
        failing.value = true
    }

    /**
     * One page of [id] before [before]; [visible]: a listed disk session whose file still exists. Else
     * `UnknownSession`; a cursor outside the file is a read failure (`Runtime(COMMAND_FAILED)`), like the real one.
     */
    internal fun read(
        id: SessionId,
        before: MessageCursor?,
        visible: Boolean,
    ): Outcome<SessionMessagePage, SessionError> {
        recorded.update { it + (id to before) }
        val failure = SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)
        if (failing.getAndUpdate { false }) return Outcome.Err(failure)
        if (!visible) return Outcome.Err(SessionError.UnknownSession(id))
        val all = messages.value[id].orEmpty()
        val end = before?.offset ?: all.size.toLong()
        if (end < 0 || end > all.size) return Outcome.Err(failure)
        val start = maxOf(0, end.toInt() - PAGE)
        val older = MessageCursor(start.toLong()).takeIf { start > 0 }
        return Outcome.Ok(SessionMessagePage(all.subList(start, end.toInt()), older))
    }

    private companion object {
        const val PAGE = 20
    }
}
