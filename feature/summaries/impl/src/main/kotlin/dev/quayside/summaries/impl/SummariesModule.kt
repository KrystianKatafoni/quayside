package dev.quayside.summaries.impl

import dev.quayside.summaries.api.SummariesApi

/** The only public entry point of this module; called from the composition root in `app`. */
public fun summariesModule(): SummariesApi = SummariesApi(LastMessageSummarizer())
