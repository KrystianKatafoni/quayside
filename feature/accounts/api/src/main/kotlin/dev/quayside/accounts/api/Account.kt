package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration

/**
 * A Claude Code login = a configuration directory (ADR 0029 p.1). [configDir] is absolute; the default account
 * is always `~/.claude` and must be run WITHOUT `CLAUDE_CONFIG_DIR` (docs/compat.md). [label] is null only for
 * the default account until renamed (the UI shows its own text). `toString` hides [label] and [configDir] (ENG-C8).
 */
public data class Account(
    val id: AccountId,
    val label: String?,
    val configDir: Path,
    val enabled: Boolean = true,
    val inAutomation: Boolean = true,
) {
    public val isDefault: Boolean get() = id == AccountId.DEFAULT

    override fun toString(): String = "Account(id=$id, enabled=$enabled, inAutomation=$inAutomation)"
}

/** FR-L2. Not stored in `accounts.json`; producers come in AM-1005 and AM-1006. */
public sealed interface AccountStatus {
    /** Usable now. */
    public data object Available : AccountStatus

    /** Since [since]; [until] null = until a manual unlock; over when `until <= now`. */
    public data class Limited(
        val since: Instant,
        val until: Instant?,
    ) : AccountStatus

    /** The account is not signed in. */
    public data object LoggedOut : AccountStatus

    /** No measurement yet. */
    public data object Unknown : AccountStatus
}

/** Content of `accounts.json`: [accounts] in automation order, the default always present; [cooldown] 1-24 h. */
public data class AccountsState(
    val accounts: List<Account>,
    val cooldown: Duration = AccountRules.DEFAULT_COOLDOWN,
)
