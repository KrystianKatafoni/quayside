package dev.quayside.summaries.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.Summarizer
import dev.quayside.summaries.api.SummaryError

/** [Summarizer] over [LastMessageSummary]: [SummaryError.Empty] when nothing is left. */
internal class LastMessageSummarizer : Summarizer {
    override fun summarize(lastMessage: String): Outcome<String, SummaryError> =
        LastMessageSummary.of(lastMessage)?.let { Outcome.Ok(it) } ?: Outcome.Err(SummaryError.Empty)
}
