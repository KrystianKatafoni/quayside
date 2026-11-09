package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.Logger
import kotlinx.coroutines.CoroutineDispatcher
import java.nio.file.Path

/**
 * Creates the [WorkspaceTrust] that edits `<configHome>/.claude.json` (ADR 0031). It writes only when its caller
 * asks, and the caller asks only after the user agreed.
 *
 * @param configHome directory with `.claude.json`; today the user's home directory.
 * @param io dispatcher for file access.
 * @param logger gets fixed categories only, never a path, the file content or an exception message (ENG-C8).
 */
public fun workspaceTrustModule(
    configHome: Path,
    io: CoroutineDispatcher,
    logger: Logger,
): WorkspaceTrust = ClaudeJsonTrust(configHome, io, logger)
