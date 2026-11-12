package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountsState
import dev.quayside.core.domain.AccountId
import java.nio.file.Path

/** The default account of a user with home directory [home]: always `~/.claude` (ADR 0035 p.2). */
internal fun defaultAccountOf(
    home: Path,
    label: String? = null,
    enabled: Boolean = true,
    inAutomation: Boolean = true,
): Account = Account(AccountId.DEFAULT, label, home.resolve(".claude"), enabled, inAutomation)

/** Only the default account: the state when nothing is stored. */
internal fun defaultStateOf(home: Path): AccountsState = AccountsState(listOf(defaultAccountOf(home)))
