package dev.quayside.accounts.api

import java.nio.file.Path

/** What `accounts:impl` offers to the app. */
public data class AccountsApi(
    val repo: AccountsRepo,
    val setup: AccountSetup,
)

/**
 * Locations the accounts module works in. [home] is the user's home directory; [backupDir] is
 * `<app support>/accounts-backup`. Both are paths of the user, so `toString` is never logged.
 */
public data class AccountPaths(
    val home: Path,
    val backupDir: Path,
) {
    override fun toString(): String = "AccountPaths"
}
