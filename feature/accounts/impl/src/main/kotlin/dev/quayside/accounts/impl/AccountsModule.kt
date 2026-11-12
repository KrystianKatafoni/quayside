package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountPaths
import dev.quayside.accounts.api.AccountsApi
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Opens the accounts stored as `accounts.json` in [storage] and the disk side of account setup. Suspends until
 * the file is loaded, so `repo.state` is final on return; never throws except cancellation.
 *
 * @param paths the user home and the backup directory `<app support>/accounts-backup`.
 * @param clock names the backup directories (`<epochMillis>-<id>`).
 * @param io the dispatcher for blocking file IO.
 * @param logger gets fixed categories and account ids only, never names or paths (ENG-C8).
 */
public suspend fun accountsModule(
    storage: JsonStorage,
    paths: AccountPaths,
    clock: Clock,
    io: CoroutineDispatcher,
    logger: Logger,
): AccountsApi {
    val spec = StoreSpec(JsonAccountsRepo.FILE_NAME, AccountsSerializer(paths.home), defaultStateOf(paths.home))
    val repo = JsonAccountsRepo.open(storage.store(spec), paths.home, logger)
    val merge = MergeIntoAccount(paths.home.resolve(".claude"), AccountBackups(paths.backupDir, clock))
    return AccountsApi(repo, FileAccountSetup(repo, paths, merge, io, logger))
}
