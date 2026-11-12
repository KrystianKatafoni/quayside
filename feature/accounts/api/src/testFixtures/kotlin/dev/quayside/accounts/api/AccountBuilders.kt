package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import java.nio.file.Path

/** The default account of a user whose home directory is [home]: `~/.claude`. */
public fun defaultAccount(
    home: Path,
    label: String? = null,
    enabled: Boolean = true,
    inAutomation: Boolean = true,
): Account = Account(AccountId.DEFAULT, label, home.resolve(".claude"), enabled, inAutomation)

/** An account the app created: `~/.claude-quayside/<slug>`, label defaults to the slug. */
public fun anAccount(
    slug: String,
    home: Path,
    label: String = slug,
): Account = Account(AccountId(slug), label, home.resolve(".claude-quayside").resolve(slug))

/** Only the default account, as for a user with nothing stored. */
public fun defaultState(home: Path): AccountsState = AccountsState(listOf(defaultAccount(home)))
