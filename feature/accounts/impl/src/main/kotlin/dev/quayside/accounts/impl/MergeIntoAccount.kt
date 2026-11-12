package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.CopyItem
import dev.quayside.accounts.api.CopyReport
import dev.quayside.accounts.api.SetupStage
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.map
import java.nio.file.Path

/**
 * The copy from `~/.claude` ([defaultDir]) into an existing account (ADR 0035 p.7): plan, back up every file that
 * would be overwritten with other content, only then write. A failed backup overwrites nothing.
 */
internal class MergeIntoAccount(
    private val defaultDir: Path,
    private val backups: AccountBackups,
) {
    fun into(
        account: Account,
        items: Set<CopyItem>,
    ): Outcome<CopyReport, AccountSetupError> =
        stage(SetupStage.COPY) { DefaultSettingsCopy.plan(defaultDir, account.configDir, items) }.flatMap { plan ->
            if (plan.blocked) return@flatMap Outcome.Err(AccountSetupError.DiskFailed(SetupStage.BACKUP))
            val backed = stage(SetupStage.BACKUP) { backups.back(account.id, account.configDir, plan.overwrites) }
            backed.flatMap { backup ->
                stage(SetupStage.COPY) { DefaultSettingsCopy.write(defaultDir, account.configDir, plan) }
                    .map { CopyReport(plan.copied, plan.skipped, backup) }
            }
        }
}
