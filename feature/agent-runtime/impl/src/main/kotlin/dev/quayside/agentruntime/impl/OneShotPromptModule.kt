package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.core.process.ProcessRunner
import java.nio.file.Path

/**
 * Creates the `claude -p` adapter of [OneShotPrompt] (ADR 0002, ADR 0019). Thread-safe and stateless.
 *
 * @param executable `claude` on the login-shell PATH, or an absolute path (tests).
 * @param homeDir working directory of the command.
 */
public fun oneShotPromptModule(
    process: ProcessRunner,
    homeDir: Path,
    executable: String = "claude",
): OneShotPrompt = ClaudePrintAdapter(process, homeDir, executable)
