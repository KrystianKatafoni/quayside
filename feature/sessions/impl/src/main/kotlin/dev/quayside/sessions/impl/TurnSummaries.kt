package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.summaries.api.Summarizer

/**
 * Summaries of the last message of each listed conversation (FR-S7, AM-604): the quick one from [summarizer] and,
 * when [refinement] is set, the model one that replaces it later. The texts are user data (ENG-C8). Not
 * thread-safe: every call is made under the mutex of `SessionsState`.
 *
 * Each `Stop` with a message takes the next number of a global counter as the turn of its conversation, so a number
 * never comes back, not even after the conversation left the list and returned.
 */
internal class TurnSummaries(
    val summarizer: Summarizer?,
    private val refinement: SummaryRefinement? = null,
) {
    private var texts: Map<ConversationId, String> = emptyMap()
    private var turns: Map<ConversationId, Long> = emptyMap()
    private var counter = 0L

    /**
     * Summarizes the message of [event] for a conversation of [listing] and keeps the old summary otherwise. The
     * model summary of the turn, when it comes, is handed to [deliver] with the turn number.
     */
    fun onStop(
        event: AgentEvent.TurnEnded,
        listing: List<RuntimeSession>,
        deliver: suspend (turn: Long, text: String) -> Unit,
    ) {
        val id = event.meta.conversationId
        val message = event.lastMessage?.takeIf { it.isNotEmpty() && listing.any { s -> s.conversationId == id } }
        if (message == null) return
        val turn = ++counter
        turns = turns + (id to turn)
        refinement?.start(id, message) { text -> deliver(turn, text) }
        val summary = summarizer?.summarize(message) as? Outcome.Ok ?: return
        texts = texts + (id to summary.value)
    }

    /** Stores the model summary [text] when [turn] is still the newest of [id]; false when it is dropped. */
    fun accept(
        id: ConversationId,
        turn: Long,
        text: String,
    ): Boolean {
        if (turns[id] != turn) return false
        texts = texts + (id to text)
        return true
    }

    /**
     * Forgets the conversations not in [listing] (their turns, texts and running refinement) and maps the rest to
     * session ids.
     */
    fun forListing(listing: List<RuntimeSession>?): Map<SessionId, String> {
        if (listing == null) return emptyMap()
        val listed = listing.mapNotNull { it.conversationId }.toSet()
        (turns.keys - listed).forEach { refinement?.cancel(it) }
        texts = texts.filterKeys { it in listed }
        turns = turns.filterKeys { it in listed }
        return listing.mapNotNull { s -> s.conversationId?.let(texts::get)?.let { s.id to it } }.toMap()
    }
}
