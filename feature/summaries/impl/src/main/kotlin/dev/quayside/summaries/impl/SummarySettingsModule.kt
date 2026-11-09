package dev.quayside.summaries.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.summaries.api.SummarySettingsRepo

/**
 * Opens the model summary switch stored as `summaries.json` in [storage] (AM-606). Suspends until the file is
 * loaded, so [SummarySettingsRepo.useModel] and [SummarySettingsRepo.startedWithModel] are final on return; never
 * throws except cancellation. Open it right next to `summariesModule` at start, so both see the same file.
 *
 * @param logger gets fixed categories only, never file contents (ENG-C8).
 */
public suspend fun summarySettingsModule(
    storage: JsonStorage,
    logger: Logger,
): SummarySettingsRepo = JsonSummarySettingsRepo.open(storage.store(summarySettingsSpec()), logger)
