package dev.agentmanager.commandpalette.impl

import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.core.domain.Logger
import kotlinx.coroutines.CoroutineScope

/**
 * Creates the registry of palette commands. [scope] lives as long as the app (like `sessionsScope` in `Main.kt`);
 * it keeps the merged list hot. [logger] gets command ids and fixed categories only, never titles (ENG-C8).
 */
public fun commandPaletteModule(
    scope: CoroutineScope,
    logger: Logger,
): CommandRegistry = InMemoryCommandRegistry(scope, logger)
