package dev.quayside.accounts.impl

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Clock
import java.nio.file.Path

/**
 * Backups of account files that a copy overwrites, in `<root>/<epochMillis>-<id>/<relative path>` with owner-only
 * permissions (ADR 0035 p.7). [root] is outside the user's directories.
 */
internal class AccountBackups(
    private val root: Path,
    private val clock: Clock,
) {
    /**
     * Copies [files] (relative to [accountDir]) into a new backup directory and returns it; null for no files, so
     * nothing is created. Throws [java.io.IOException]; then the caller must not overwrite anything.
     */
    fun back(
        id: AccountId,
        accountDir: Path,
        files: List<Path>,
    ): Path? {
        if (files.isEmpty()) return null
        val dir = root.resolve("${clock.now().toEpochMilli()}-$id")
        files.forEach { AccountDirectories.place(accountDir.resolve(it), dir.resolve(it), ownerOnly = true) }
        return dir
    }
}
