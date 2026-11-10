package dev.quayside.sessions.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.sessions.api.SessionHistorySettingsRepo

/**
 * Opens the settings of the disk sessions stored as `session-history.json` in [storage] (ADR 0032). Suspends until
 * the file is loaded, so `settings` is final on return; never throws except cancellation.
 *
 * @param logger gets fixed categories only, never file contents (ENG-C8).
 */
public suspend fun sessionHistorySettingsModule(
    storage: JsonStorage,
    logger: Logger,
): SessionHistorySettingsRepo = JsonSessionHistorySettingsRepo.open(storage.store(sessionHistorySettingsSpec()), logger)
