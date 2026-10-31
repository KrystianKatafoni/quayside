package dev.quayside.terminal.impl

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * PTY terminals with JediTerm widgets. [shell] is the login shell, used for commands and for `LOGIN_DEFAULT`
 * (usually `LoginShellProcessRunner.defaultShell()`). [settings] is read at every open, so a change applies to
 * tabs opened later and never to open ones (ADR 0016). [scope] lives as long as the app; it disposes widgets on
 * [AppDispatchers.main]. [logger] gets ids, kinds and fixed categories only, never arguments or environment
 * (ENG-C8). If [scope] is cancelled before a widget is disposed, the processes are already killed but the widget
 * stays open.
 */
public fun terminalModule(
    dispatchers: AppDispatchers,
    scope: CoroutineScope,
    logger: Logger,
    shell: Path,
    settings: StateFlow<TerminalSettings>,
): TerminalApi = terminalModule(dispatchers, scope, logger, TerminalLaunch(shell, settings))

/** As the public overload, with the environment of the child processes given (tests). */
internal fun terminalModule(
    dispatchers: AppDispatchers,
    scope: CoroutineScope,
    logger: Logger,
    launch: TerminalLaunch,
): TerminalApi = JediTermTerminalApi(SessionContext(dispatchers, scope, logger), launch)

/**
 * Opens `terminal.json` in [storage]; suspends until loaded, so [TerminalSettingsRepo.issue] is final on return.
 * Never throws except cancellation. [logger] gets fixed categories only, never values (ENG-C8).
 */
public suspend fun terminalSettingsModule(
    storage: JsonStorage,
    logger: Logger,
): TerminalSettingsRepo =
    JsonTerminalSettingsRepo.open(
        storage.store(StoreSpec(JsonTerminalSettingsRepo.FILE_NAME, TerminalSettingsSerializer, TerminalSettings())),
        logger,
    )
