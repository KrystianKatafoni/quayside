package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStorage
import dev.quayside.summaries.api.SummariesApi

/**
 * The only public entry point of this module; called from the composition root in `app`. Reads `summaries.json`
 * once: with `useModel: true` the result has a refiner that asks [prompt], otherwise (no file, a broken file,
 * a file of a newer version) it has none. Never throws except cancellation.
 *
 * @param logger gets fixed categories only, never file contents or messages (ENG-C8).
 */
public suspend fun summariesModule(
    storage: JsonStorage,
    prompt: OneShotPrompt,
    logger: Logger,
): SummariesApi {
    val loaded = storage.store(summarySettingsSpec()).load()
    val useModel = loaded is Outcome.Ok && loaded.value.value.useModel
    if (loaded is Outcome.Err) logger.warn("summaries settings not loaded", "error" to loaded.error::class.simpleName)
    return SummariesApi(LastMessageSummarizer(), if (useModel) HaikuSummaryRefiner(prompt) else null)
}
