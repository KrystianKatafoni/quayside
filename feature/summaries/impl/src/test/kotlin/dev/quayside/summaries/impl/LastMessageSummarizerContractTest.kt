package dev.quayside.summaries.impl

import dev.quayside.summaries.api.Summarizer
import dev.quayside.summaries.api.SummarizerContract

/** The summarizer given out by `summariesModule` passes the shared contract (ENG-T2). */
class LastMessageSummarizerContractTest : SummarizerContract() {
    override fun summarizer(): Summarizer = summariesModule().summarizer
}
