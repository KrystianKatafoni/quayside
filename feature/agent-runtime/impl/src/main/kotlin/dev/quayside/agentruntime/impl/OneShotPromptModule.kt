package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.core.domain.AccountId
import dev.quayside.core.process.ProcessRunner
import java.nio.file.Path

/**
 * Creates the `claude -p` adapter of [OneShotPrompt] (ADR 0002, ADR 0019). Thread-safe and stateless.
 *
 * @param executable `claude` on the login-shell PATH, or an absolute path (tests).
 * @param homeDir working directory of the command.
 * @param account Claude account; [AccountId.DEFAULT] never gets `CLAUDE_CONFIG_DIR`.
 * @param configDir config directory of [account]; ignored for the default account.
 * @throws IllegalArgumentException when a non-default [account] has no [configDir] (programmer error).
 */
public fun oneShotPromptModule(
    process: ProcessRunner,
    homeDir: Path,
    executable: String = "claude",
    account: AccountId = AccountId.DEFAULT,
    configDir: Path? = null,
): OneShotPrompt = ClaudePrintAdapter(process, homeDir, executable, accountEnv(account, configDir))
