package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountPaths
import dev.quayside.accounts.api.AccountRules
import dev.quayside.accounts.api.AccountSetup
import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.AccountsRepo
import dev.quayside.accounts.api.CopyItem
import dev.quayside.accounts.api.CopyReport
import dev.quayside.accounts.api.NewAccount
import dev.quayside.accounts.api.SetupStage
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/**
 * [AccountSetup] on the real file system (ADR 0035 p.5-7). Every operation runs on [io] and is not cancellable
 * once started, so no half-built directory outlives a cancelled caller. Logs carry stages and ids only (ENG-C8).
 */
internal class FileAccountSetup(
    private val repo: AccountsRepo,
    private val paths: AccountPaths,
    private val merge: MergeIntoAccount,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : AccountSetup {
    private val defaultDir: Path = paths.home.resolve(".claude")

    override suspend fun create(request: NewAccount): Outcome<Account, AccountSetupError> =
        uncancellable {
            val id = AccountRules.slug(request.label) ?: return@uncancellable Outcome.Err(AccountSetupError.InvalidName)
            val dir = paths.home.resolve(".claude-quayside").resolve(id.value)
            if (isTaken(id, dir) || Files.exists(dir, NOFOLLOW_LINKS)) {
                return@uncancellable Outcome.Err(AccountSetupError.AlreadyExists)
            }
            val made = AccountDirectories.create(dir)
            if (made is Outcome.Err) return@uncancellable made
            val result = fill(dir, request).flatMap { register(Account(id, request.label, dir)) }
            if (result is Outcome.Err) cleanUp(dir, id)
            result
        }

    override suspend fun register(
        label: String,
        dir: Path,
    ): Outcome<Account, AccountSetupError> =
        uncancellable {
            val id = AccountRules.slug(label) ?: return@uncancellable Outcome.Err(AccountSetupError.InvalidName)
            val absolute = dir.toAbsolutePath().normalize()
            when {
                !Files.isDirectory(dir) -> Outcome.Err(AccountSetupError.NotADirectory)
                realPath(dir) == realPath(defaultDir) -> Outcome.Err(AccountSetupError.DefaultDirectory)
                isTaken(id, absolute) -> Outcome.Err(AccountSetupError.AlreadyExists)
                else -> register(Account(id, label, absolute))
            }
        }

    override suspend fun copyFromDefault(
        id: AccountId,
        items: Set<CopyItem>,
    ): Outcome<CopyReport, AccountSetupError> =
        uncancellable {
            val account =
                repo.state.value.accounts
                    .firstOrNull { it.id == id }
            when {
                id == AccountId.DEFAULT -> Outcome.Err(AccountSetupError.DefaultDirectory)
                account == null -> Outcome.Err(AccountSetupError.UnknownAccount)
                else -> merge.into(account, items)
            }.also { result -> logFailure(result, id) }
        }

    /** The projects link and the chosen items of a freshly created [dir]. */
    private fun fill(
        dir: Path,
        request: NewAccount,
    ): Outcome<Unit, AccountSetupError> =
        linkProjects(dir, request.shareProjects).flatMap {
            stage(SetupStage.COPY) {
                val plan = DefaultSettingsCopy.plan(defaultDir, dir, request.copy)
                DefaultSettingsCopy.write(defaultDir, dir, plan)
            }
        }

    private fun linkProjects(
        dir: Path,
        share: Boolean,
    ): Outcome<Unit, AccountSetupError> =
        if (share) {
            AccountDirectories.linkProjects(dir, defaultDir.resolve(AccountDirectories.PROJECTS))
        } else {
            Outcome.Ok(Unit)
        }

    private suspend fun register(account: Account): Outcome<Account, AccountSetupError> =
        when (val result = repo.update { it.copy(accounts = it.accounts + account) }) {
            is Outcome.Ok -> Outcome.Ok(account)
            is Outcome.Err -> Outcome.Err(AccountSetupError.Registry(result.error))
        }

    private fun isTaken(
        id: AccountId,
        dir: Path,
    ): Boolean =
        repo.state.value.accounts
            .any { it.id == id || it.configDir.normalize() == dir.normalize() }

    private fun cleanUp(
        dir: Path,
        id: AccountId,
    ) {
        logger.warn("account setup failed, removing the new directory", "account" to id.value)
        if (AccountDirectories.deleteTree(dir) is Outcome.Err) {
            logger.warn("account setup cleanup failed", "account" to id.value)
        }
    }

    private fun logFailure(
        result: Outcome<CopyReport, AccountSetupError>,
        id: AccountId,
    ) {
        if (result is Outcome.Err) logger.warn("copy to an account failed", "account" to id.value)
    }

    private suspend fun <T> uncancellable(block: suspend () -> T): T = withContext(NonCancellable + io) { block() }
}
