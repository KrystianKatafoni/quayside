package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.core.domain.Logger
import kotlinx.coroutines.CoroutineDispatcher
import java.nio.file.Path

/**
 * Creates the read-only [SessionHistory] over `<claudeHome>/projects/<dir>/<uuid>.jsonl` (ADR 0032).
 *
 * @param claudeHome the Claude Code data directory (`~/.claude`).
 * @param io dispatcher for file access.
 * @param logger gets counts and fixed categories only, never a path, a UUID or text from a file (ENG-C8).
 */
public fun sessionHistoryModule(
    claudeHome: Path,
    io: CoroutineDispatcher,
    logger: Logger,
): SessionHistory = FileSessionHistory(claudeHome.resolve("projects"), io, logger)
