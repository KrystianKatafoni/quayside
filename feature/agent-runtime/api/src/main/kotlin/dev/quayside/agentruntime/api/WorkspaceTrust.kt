package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path

/**
 * Trusts one workspace directory in Claude Code's user config (`.claude.json`, ADR 0031) so that `claude --bg` may
 * start there. Call only after the user agreed. May be called from any dispatcher; never blocks the calling thread and
 * never throws except cancellation.
 */
public fun interface WorkspaceTrust {
    /**
     * Sets `hasTrustDialogAccepted` to true for the real path of [dir] (`toRealPath`) only, never for its parents, and
     * keeps every other value of the file. `Ok` without writing when it is already true. Never writes when the file
     * is missing, unreadable or not in the expected shape; the write is atomic and retried on a concurrent change.
     */
    public suspend fun trust(dir: Path): Outcome<Unit, TrustError>
}

/** Why [WorkspaceTrust.trust] wrote nothing. Fixed categories, never paths or file content (ENG-C8). */
public enum class TrustError {
    /** The directory does not exist or is not a directory. */
    DIRECTORY_MISSING,

    /** The config file does not exist; Claude Code has never run for this user. */
    CONFIG_MISSING,

    /** The config file is a symbolic link or larger than 16 MiB. */
    CONFIG_UNSUPPORTED,

    /** The config file could not be read. */
    CONFIG_UNREADABLE,

    /** Not UTF-8 JSON in the expected shape (root, `projects`, entry objects; boolean flag). */
    CONFIG_INVALID,

    /** Another process changed the file on every attempt. */
    CONCURRENT_CHANGE,

    /** The temporary file or the atomic move failed. */
    WRITE_FAILED,
}
