package dev.quayside.summaries.api

/** What `summariesModule` gives the composition root. */
public data class SummariesApi(
    val summarizer: Summarizer,
)
