package dev.quayside.app

import dev.quayside.commandpalette.api.CommandRegistry
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRepo
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/** The modules the window is built from; keeps [MainWindow] within the parameter limit of detekt. */
internal class AppModules(
    val projects: ProjectsApi,
    val sessions: SessionsApi,
    val inspector: ProjectInspector,
    val registry: CommandRegistry,
    val terminal: TerminalDeps,
)

/**
 * The terminal module with what the window needs around it: the settings and the login shell behind the label and
 * start directory of shell tabs, and the attach command.
 */
internal class TerminalDeps(
    val api: TerminalApi,
    val repo: TerminalSettingsRepo,
    val loginShell: Path,
    val attachCommand: (SessionId) -> ShellCommand,
) {
    /** The current settings, read from [repo]. */
    val settings: StateFlow<TerminalSettings> get() = repo.settings
}
