package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import java.nio.file.Path

/** Disk side of accounts (FR-L1, FR-L9). Any dispatcher; IO on the injected one; never throws. */
public interface AccountSetup {
    /**
     * Creates `~/.claude-quayside/<slug>/` (link `projects` when [NewAccount.shareProjects], copies [NewAccount.copy]
     * from `~/.claude`), then appends the account to [AccountsRepo]. On any error the new directory is removed and
     * nothing is registered. Not cancellable once started.
     *
     * Warning: the shared `projects/` is not fully measured (ADR 0035, AM-1001 point 7).
     */
    public suspend fun create(request: NewAccount): Outcome<Account, AccountSetupError>

    /** Registers an existing directory; never writes into it. */
    public suspend fun register(
        label: String,
        dir: Path,
    ): Outcome<Account, AccountSetupError>

    /**
     * Copies [items] from `~/.claude` into account [id], merging by relative path; every overwritten file with other
     * content is first backed up ([CopyReport.backupDir]); a failed backup overwrites nothing.
     */
    public suspend fun copyFromDefault(
        id: AccountId,
        items: Set<CopyItem>,
    ): Outcome<CopyReport, AccountSetupError>
}

/** A request to create an account. `toString` hides [label] (ENG-C8). */
public data class NewAccount(
    val label: String,
    val copy: Set<CopyItem> = CopyItem.entries.toSet(),
    val shareProjects: Boolean = true,
) {
    override fun toString(): String = "NewAccount(copy=$copy, shareProjects=$shareProjects)"
}

/** What may be copied from the default account; never `plugins/`, `.claude.json`, login or supervisor state. */
public enum class CopyItem {
    /** `CLAUDE.md`. */
    INSTRUCTIONS,

    /** `agents/`. */
    AGENTS,

    /** `skills/`. */
    SKILLS,

    /** `commands/`. */
    COMMANDS,

    /** `settings.json`. */
    SETTINGS,
}

/** [copied] and [skipped] (missing on the default account) items; [backupDir] null when nothing was overwritten. */
public data class CopyReport(
    val copied: Set<CopyItem>,
    val skipped: Set<CopyItem>,
    val backupDir: Path?,
) {
    override fun toString(): String = "CopyReport(copied=$copied, skipped=$skipped, backup=${backupDir != null})"
}

/** Why an [AccountSetup] operation changed nothing. */
public sealed interface AccountSetupError {
    /** Slug empty, `default` or `auto`. */
    public data object InvalidName : AccountSetupError

    /** Id or directory already registered, or the create target exists. */
    public data object AlreadyExists : AccountSetupError

    /** The given path is not a directory. */
    public data object NotADirectory : AccountSetupError

    /** `~/.claude` (by real path) or a copy into the default account. */
    public data object DefaultDirectory : AccountSetupError

    /** No registered account has this id. */
    public data object UnknownAccount : AccountSetupError

    /** A disk operation failed at [stage]. */
    public data class DiskFailed(
        val stage: SetupStage,
    ) : AccountSetupError

    /** The registry ([AccountsRepo]) refused the change with [error]. */
    public data class Registry(
        val error: AccountsError,
    ) : AccountSetupError
}

/** Where a disk operation failed. */
public enum class SetupStage { DIRECTORY, LINK, COPY, BACKUP }
