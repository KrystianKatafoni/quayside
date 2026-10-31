package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.process.ProcessRunner
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates the Claude Code CLI adapter of [AgentRuntime] (ADR 0002). Thread-safe and stateless.
 * The long parameter list is deliberate: `impl` may expose only this factory, so no public settings type.
 *
 * @param executable `claude` on the login-shell PATH, or an absolute path (tests).
 * @param homeDir working directory of every command except `start`, and of [AgentRuntime.attachCommand].
 * @param commandTimeout limit of `agents`, `logs`, `stop`, `respawn`, `rm`; [startTimeout] limits `--bg` (docs/11 §6).
 */
@Suppress("LongParameterList")
public fun agentRuntimeModule(
    process: ProcessRunner,
    clock: Clock,
    logger: Logger,
    homeDir: Path,
    executable: String = "claude",
    commandTimeout: Duration = 10.seconds,
    startTimeout: Duration = 30.seconds,
): AgentRuntime = ClaudeCliAdapter(process, clock, logger, CliConfig(homeDir, executable, commandTimeout, startTimeout))
