package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** One call of [SessionHistory.list] as [FakeSessionHistory] recorded it. */
public data class HistoryCall(
    val dirs: List<Path>,
    val since: Instant?,
)

/** One call of [SessionHistory.conversation] as [FakeSessionHistory] recorded it. */
public data class ConversationCall(
    val conversation: ConversationId,
    val startCwd: Path,
    val before: HistoryCursor?,
    val limit: Int,
)

/**
 * Scriptable in-memory [SessionHistory] (ENG-I7). Passes [SessionHistoryContract].
 *
 * Lists the [conversations] it holds by the same rules as the file-based implementation: start directory in
 * `dirs` or below (by path components), `modifiedAt` at or after `since`, newest first, each once. Every call is
 * recorded in [calls]. A scripted failure ([failNext], [failing]) comes first and changes nothing.
 *
 * [conversation] serves the messages scripted with [putMessages]: the cursor is the index of the oldest message of
 * the page, so a page before cursor `n` holds the messages `n - limit` until `n`. An id that is not a lowercase UUID,
 * or that is neither listed nor scripted, or whose `startCwd` differs from the start directory of the listed
 * conversation, is `NOT_FOUND`; a cursor past the end is `UNREADABLE`. Calls are recorded in
 * [conversationCalls]; [failNextConversation] scripts one failure.
 */
public class FakeSessionHistory(
    conversations: List<PastConversation> = emptyList(),
) : SessionHistory {
    private val state = AtomicReference(State(conversations))

    /** Every call so far, in order, also the failed ones. */
    public val calls: List<HistoryCall> get() = state.get().calls

    /** Every call of [conversation] so far, in order, also the failed ones. */
    public val conversationCalls: List<ConversationCall> get() = state.get().conversationCalls

    /** The conversations the next call can return. */
    public val conversations: List<PastConversation> get() = state.get().conversations

    /** Adds [conversation], or replaces the one with the same id. */
    public fun put(conversation: PastConversation) {
        state.updateAndGet { before ->
            before.copy(
                conversations =
                    before.conversations.filterNot { it.conversationId == conversation.conversationId } +
                        conversation,
            )
        }
    }

    /** Removes the conversation [id] and its scripted messages (a file deleted outside the app). */
    public fun delete(id: ConversationId) {
        state.updateAndGet { before ->
            before.copy(
                conversations = before.conversations.filterNot { it.conversationId == id },
                messages = before.messages - id,
            )
        }
    }

    /** Scripts the messages of [id], oldest first; replaces earlier ones. The conversation then exists. */
    public fun putMessages(
        id: ConversationId,
        messages: List<ConversationMessage>,
    ) {
        state.updateAndGet { it.copy(messages = it.messages + (id to messages)) }
    }

    /** The next [conversation] call returns `Err(error)`; later calls work again. */
    public fun failNextConversation(error: ConversationError) {
        state.updateAndGet { it.copy(nextConversationFailure = error) }
    }

    /** The next call returns `Err(error)`; later calls work again. */
    public fun failNext(error: HistoryError) {
        state.updateAndGet { it.copy(nextFailure = error) }
    }

    /** While set, every call returns `Err(error)`; null = works again. */
    public fun failing(error: HistoryError?) {
        state.updateAndGet { it.copy(failure = error) }
    }

    override suspend fun list(
        dirs: List<Path>,
        since: Instant?,
    ): Outcome<List<PastConversation>, HistoryError> {
        val before = state.getAndUpdate { it.copy(calls = it.calls + HistoryCall(dirs, since), nextFailure = null) }
        val failure = before.nextFailure ?: before.failure
        if (failure != null) return Outcome.Err(failure)
        val listed =
            before.conversations
                .filter { c -> dirs.any { c.startCwd.startsWith(it) } && (since == null || c.modifiedAt >= since) }
                .sortedByDescending { it.modifiedAt }
        return Outcome.Ok(listed)
    }

    override suspend fun conversation(
        conversation: ConversationId,
        startCwd: Path,
        before: HistoryCursor?,
        limit: Int,
    ): Outcome<ConversationPage, ConversationError> {
        val call = ConversationCall(conversation, startCwd, before, limit)
        val seen =
            state.getAndUpdate {
                it.copy(conversationCalls = it.conversationCalls + call, nextConversationFailure = null)
            }
        seen.nextConversationFailure?.let { return Outcome.Err(it) }
        if (!UUID_PATTERN.matches(conversation.value) || !seen.holds(conversation, startCwd)) {
            return Outcome.Err(ConversationError.NOT_FOUND)
        }
        val all = seen.messages[conversation].orEmpty()
        val end = before?.offset ?: all.size.toLong()
        if (end < 0 || end > all.size) return Outcome.Err(ConversationError.UNREADABLE)
        val start = maxOf(0, end.toInt() - limit.coerceIn(1, MAX_LIMIT))
        val older = if (start == 0) null else HistoryCursor(start.toLong())
        return Outcome.Ok(ConversationPage(all.subList(start, end.toInt()), older))
    }

    // A listed conversation lives under its start directory only; scripted messages alone have no directory.
    private fun State.holds(
        id: ConversationId,
        startCwd: Path,
    ): Boolean {
        val listed = conversations.firstOrNull { it.conversationId == id }
        return if (listed != null) listed.startCwd == startCwd else messages.containsKey(id)
    }

    // `list + path` would add the path's name elements: a Path is an Iterable<Path>; the lists here hold records.
    private data class State(
        val conversations: List<PastConversation> = emptyList(),
        val calls: List<HistoryCall> = emptyList(),
        val nextFailure: HistoryError? = null,
        val failure: HistoryError? = null,
        val messages: Map<ConversationId, List<ConversationMessage>> = emptyMap(),
        val conversationCalls: List<ConversationCall> = emptyList(),
        val nextConversationFailure: ConversationError? = null,
    )

    private companion object {
        const val MAX_LIMIT = 100
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
