package dev.quayside.app

import dev.quayside.commandpalette.api.CommandRegistry
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.terminal.api.TerminalApi

/** The modules the window is built from; keeps [MainWindow] within the parameter limit of detekt. */
internal class AppModules(
    val projects: ProjectsApi,
    val sessions: SessionsApi,
    val inspector: ProjectInspector,
    val registry: CommandRegistry,
    val terminal: TerminalDeps,
)

/** The terminal module with what the window needs around it: the shell tab label and the attach command. */
internal class TerminalDeps(
    val api: TerminalApi,
    val shellLabel: String,
    val attachCommand: (SessionId) -> ShellCommand,
)
