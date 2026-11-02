package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome

/**
 * A better summary that may take seconds and cost tokens (ADR 0019); same guarantees as [Summarizer]: an
 * [Outcome.Ok] summary is one line, not blank and at most [Summarizer.MAX_LENGTH] code points, and the message
 * and the summary are never logged. Any dispatcher; never throws except CancellationException.
 */
public fun interface SummaryRefiner {
    /** The summary of [lastMessage], or the reason there is none. */
    public suspend fun refine(lastMessage: String): Outcome<String, SummaryError>
}
