package dev.quayside.terminal.impl

import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * What a new terminal starts with. [shell]: the login shell, used for commands and for `LOGIN_DEFAULT`
 * (usually `LoginShellProcessRunner.defaultShell()`). [settings] is read at every open, so a change applies to
 * tabs opened later and never to open ones (ADR 0016).
 */
internal class TerminalLaunch(
    val shell: Path,
    val settings: StateFlow<TerminalSettings>,
    /** The environment a shell inherits before the module adds its own variables; tests pass a fixed map. */
    val baseEnvironment: Map<String, String> = System.getenv(),
)
