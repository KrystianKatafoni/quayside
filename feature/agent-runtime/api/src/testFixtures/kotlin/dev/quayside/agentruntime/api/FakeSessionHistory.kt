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

/**
 * Scriptable in-memory [SessionHistory] (ENG-I7). Passes [SessionHistoryContract].
 *
 * Lists the [conversations] it holds by the same rules as the file-based implementation: start directory in
 * `dirs` or below (by path components), `modifiedAt` at or after `since`, newest first, each once. Every call is
 * recorded in [calls]. A scripted failure ([failNext], [failing]) comes first and changes nothing.
 */
public class FakeSessionHistory(
    conversations: List<PastConversation> = emptyList(),
) : SessionHistory {
    private val state = AtomicReference(State(conversations))

    /** Every call so far, in order, also the failed ones. */
    public val calls: List<HistoryCall> get() = state.get().calls

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

    /** Removes the conversation [id] (a file deleted outside the app). */
    public fun delete(id: ConversationId) {
        state.updateAndGet { before ->
            before.copy(conversations = before.conversations.filterNot { it.conversationId == id })
        }
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

    // `list + path` would add the path's name elements: a Path is an Iterable<Path>; the lists here hold records.
    private data class State(
        val conversations: List<PastConversation> = emptyList(),
        val calls: List<HistoryCall> = emptyList(),
        val nextFailure: HistoryError? = null,
        val failure: HistoryError? = null,
    )
}
