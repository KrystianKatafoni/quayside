package dev.quayside.summaries.api

/**
 * What `summariesModule` gives the composition root.
 *
 * @property summarizer the summary at once, without a model.
 * @property refiner the summary by a model that replaces [summarizer]'s in the background; null = model off.
 */
public data class SummariesApi(
    val summarizer: Summarizer,
    val refiner: SummaryRefiner? = null,
)
