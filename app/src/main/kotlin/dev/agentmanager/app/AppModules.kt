package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.projects.api.ProjectInspector
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.terminal.api.TerminalApi

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
