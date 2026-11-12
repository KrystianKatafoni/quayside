package dev.quayside.accounts.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/** Stored list of accounts (`accounts.json`, ADR 0035). Any dispatcher; never throws. */
public interface AccountsRepo {
    /** Current list; only the default account when nothing is stored or the file cannot be used. */
    public val state: StateFlow<AccountsState>

    /** Validates `change(current)` by [AccountRules.validate], saves, then publishes; publishes nothing on error. */
    public suspend fun update(change: (AccountsState) -> AccountsState): Outcome<AccountsState, AccountsError>
}

/** Why [AccountsRepo.update] changed nothing. */
public sealed interface AccountsError {
    /** The file could not be written. */
    public data object WriteFailed : AccountsError

    /** The file was written by a newer version; it is never overwritten. */
    public data object NewerSchema : AccountsError

    /** The change broke the invariant [problem] of [AccountRules.validate]; nothing was saved. */
    public data class Invalid(
        val problem: AccountProblem,
    ) : AccountsError
}

/** An invariant of [AccountsState] that a change broke. */
public enum class AccountProblem {
    /** The default account is absent from the list. */
    DEFAULT_MISSING,

    /** The default account's config directory differs from the user home; it must stay unchanged. */
    DEFAULT_DIR_CHANGED,

    /** Two accounts share the same id. */
    DUPLICATE_ID,

    /** An account uses a reserved id (`auto`). */
    RESERVED_ID,

    /** Two accounts point to the same config directory after `normalize`. */
    DUPLICATE_DIR,

    /** An account's config directory is not an absolute path. */
    RELATIVE_DIR,

    /** An account's label is blank. */
    BLANK_LABEL,

    /** The cooldown lies outside [AccountRules.COOLDOWN_RANGE]. */
    COOLDOWN_OUT_OF_RANGE,
}
