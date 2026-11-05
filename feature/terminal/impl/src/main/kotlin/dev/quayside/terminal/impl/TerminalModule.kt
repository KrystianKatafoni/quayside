package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * PTY terminals with JediTerm widgets. [shell] is the login shell, used for commands and for `LOGIN_DEFAULT`
 * (usually `LoginShellProcessRunner.defaultShell()`). [settings] and [colors] are read at every open, so a change
 * applies to tabs opened later and never to open ones (ADR 0016); [colors] gives the default text (`termText`) and
 * background (`termBg`) of the widget, alpha ignored. The module owns an app-long scope on [AppDispatchers.default]
 * that disposes widgets on [AppDispatchers.main]. [logger] gets ids, kinds and fixed categories only, never
 * arguments or environment (ENG-C8).
 */
public fun terminalModule(
    dispatchers: AppDispatchers,
    logger: Logger,
    shell: Path,
    settings: StateFlow<TerminalSettings>,
    colors: StateFlow<TerminalColors>,
): TerminalApi =
    terminalModule(
        dispatchers,
        CoroutineScope(SupervisorJob() + dispatchers.default),
        logger,
        TerminalLaunch(shell, settings, colors),
    )

/** As the public overload, with the scope and the environment of the child processes given (tests). */
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
