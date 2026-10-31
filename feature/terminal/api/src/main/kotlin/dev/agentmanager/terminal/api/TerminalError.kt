package dev.agentmanager.terminal.api

/** Opaque id of an open terminal, unique within one [TerminalApi]; safe to log. */
@JvmInline
public value class TerminalId(
    public val value: String,
)

/** Why a terminal did not open or did not take input. Never carries exception text (ENG-C8). */
public sealed interface TerminalError {
    /** The working directory does not exist or is not a directory. */
    public data object WorkDirMissing : TerminalError

    /** The PTY or the widget did not start (pty4j, missing shell binary, native library). */
    public data object StartFailed : TerminalError

    /** The session or the whole [TerminalApi] was closed. */
    public data object Closed : TerminalError
}
