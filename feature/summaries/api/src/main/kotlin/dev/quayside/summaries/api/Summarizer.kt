package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome

/**
 * Turns the last message of an agent's turn (`last_assistant_message` of the `Stop` hook) into a short
 * summary at once, without IO (FR-S7). An [Outcome.Ok] summary is one line, not blank and at most
 * [MAX_LENGTH] code points. Pure and thread-safe; never throws. The message and the summary are user data
 * (ENG-C8): never logged.
 */
public fun interface Summarizer {
    /** The summary of [lastMessage], or the reason there is none. */
    public fun summarize(lastMessage: String): Outcome<String, SummaryError>

    public companion object {
        /** Longest summary, in code points. */
        public const val MAX_LENGTH: Int = 160
    }
}
