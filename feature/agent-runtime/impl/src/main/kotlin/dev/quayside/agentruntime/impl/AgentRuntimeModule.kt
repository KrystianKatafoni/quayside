package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.process.ProcessRunner
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates the Claude Code CLI adapter of [AgentRuntime] (ADR 0002). Thread-safe and stateless.
 * The long parameter list is deliberate: `impl` may expose only this factory, so no public settings type.
 *
 * @param executable `claude` on the login-shell PATH, or an absolute path (tests).
 * @param homeDir working directory of every command except `start`, and of [AgentRuntime.attachCommand].
 * @param injectHookToken when true, `start` and `respawn` pass the Keychain hook token (created on first use)
 *   to `claude` as `AM_HOOK_TOKEN`; the hooks from `HookInstaller` need it to authenticate. Off by default.
 * @param account Claude account of this supervisor; [AccountId.DEFAULT] never gets `CLAUDE_CONFIG_DIR`.
 * @param configDir config directory of [account]; ignored for the default account.
 * @param commandTimeout limit of `agents`, `logs`, `stop`, `respawn`, `rm`; [startTimeout] limits `--bg` (docs/11 §6).
 * @throws IllegalArgumentException when a non-default [account] has no [configDir] (programmer error).
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
    injectHookToken: Boolean = false,
    account: AccountId = AccountId.DEFAULT,
    configDir: Path? = null,
): AgentRuntime {
    val tokens = if (injectHookToken) KeychainHookTokenStore(process, homeDir) else null
    val config =
        CliConfig(homeDir, executable, commandTimeout, startTimeout, tokens, accountEnv(account, configDir), account)
    return ClaudeCliAdapter(process, clock, logger, config)
}

/** The environment of [account]; a non-default account without [configDir] is a programmer error. */
internal fun accountEnv(
    account: AccountId,
    configDir: Path?,
): Map<String, String> {
    require(account == AccountId.DEFAULT || configDir != null) { "account ${account.value} needs a configDir" }
    return ClaudeEnv.forAccount(account, configDir)
}
