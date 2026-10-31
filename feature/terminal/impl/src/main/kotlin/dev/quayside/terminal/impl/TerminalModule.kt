package dev.quayside.terminal.impl

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Logger
import dev.quayside.terminal.api.TerminalApi
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * PTY terminals with JediTerm widgets. [shell]: usually `LoginShellProcessRunner.defaultShell()`.
 * [scope] lives as long as the app; it disposes widgets on [AppDispatchers.main]. [logger] gets ids,
 * kinds and fixed categories only, never arguments or environment (ENG-C8). If [scope] is cancelled before
 * a widget is disposed, the processes are already killed but the widget stays open.
 */
public fun terminalModule(
    dispatchers: AppDispatchers,
    scope: CoroutineScope,
    logger: Logger,
    shell: Path,
    baseEnvironment: Map<String, String> = System.getenv(),
): TerminalApi = JediTermTerminalApi(SessionContext(dispatchers, scope, logger), shell, baseEnvironment)
