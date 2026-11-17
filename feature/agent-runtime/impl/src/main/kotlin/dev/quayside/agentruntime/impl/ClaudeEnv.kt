package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.AccountId
import java.nio.file.Path

/** Name of the variable that points `claude` at the config directory of an account. */
internal const val CONFIG_DIR_ENV = "CLAUDE_CONFIG_DIR"

/** Environment rule of an account (AM-1003, docs/compat.md). */
internal object ClaudeEnv {
    /**
     * `CLAUDE_CONFIG_DIR` for a non-default [account]; never for [AccountId.DEFAULT], even with a [configDir]:
     * the default account looks for `~/.claude/.claude.json` and reports `loggedIn: false` with the variable set.
     */
    fun forAccount(
        account: AccountId,
        configDir: Path?,
    ): Map<String, String> =
        if (account == AccountId.DEFAULT || configDir == null) {
            emptyMap()
        } else {
            mapOf(CONFIG_DIR_ENV to configDir.toString())
        }
}
