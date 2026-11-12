package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import java.time.Instant

/** Pure choice of an account for a new session, a summary or a switch target (ADR 0029 p.5). */
public object AccountSelector {
    /**
     * [preferred] when enabled and not blocked (logged out or still limited; an unknown status does not block the
     * user's own choice); else the first enabled, in-automation account in order that is [AccountStatus.Available]
     * (or an over [AccountStatus.Limited]), then the first [AccountStatus.Unknown]; a missing status = Unknown;
     * null when none.
     */
    public fun choose(
        accounts: List<Account>,
        statuses: Map<AccountId, AccountStatus>,
        preferred: AccountId?,
        now: Instant,
    ): AccountId? {
        fun kind(account: Account) = Kind.of(statuses[account.id] ?: AccountStatus.Unknown, now)

        val own = accounts.firstOrNull { it.id == preferred && it.enabled && kind(it) != Kind.BLOCKED }
        if (own != null) return own.id
        val automatic = accounts.filter { it.enabled && it.inAutomation }
        return (automatic.firstOrNull { kind(it) == Kind.FREE } ?: automatic.firstOrNull { kind(it) == Kind.UNKNOWN })
            ?.id
    }

    private enum class Kind {
        FREE,
        UNKNOWN,
        BLOCKED,
        ;

        companion object {
            fun of(
                status: AccountStatus,
                now: Instant,
            ): Kind =
                when (status) {
                    AccountStatus.Available -> FREE
                    AccountStatus.Unknown -> UNKNOWN
                    AccountStatus.LoggedOut -> BLOCKED
                    is AccountStatus.Limited -> if (status.until?.isAfter(now) == false) FREE else BLOCKED
                }
        }
    }
}
