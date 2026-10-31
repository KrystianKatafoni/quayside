package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import java.nio.file.Path

/**
 * Opens terminals backed by a PTY (FR-T1). Open functions are safe on any dispatcher: the process starts
 * on IO, the widget is built on the Swing event thread. [close] closes every open session and makes later
 * opens fail with [TerminalError.Closed]; `app` calls it on exit (also from a shutdown hook). Idempotent.
 */
public interface TerminalApi : AutoCloseable {
    /** Starts the user's login shell (`<shell> -l`) in [dir], e.g. the project or its worktree. */
    public suspend fun openShell(dir: Path): Outcome<TerminalSession, TerminalError>

    /**
     * Runs [command] in a PTY through the login shell (`exec`, so the program replaces the shell and its exit
     * code is the session's). Meant for `AgentRuntime.attachCommand(id)` and `claude logs`. A program that is
     * not found is not an error here: the shell prints its message and the session exits with 127.
     */
    public suspend fun openCommand(command: ShellCommand): Outcome<TerminalSession, TerminalError>
}
