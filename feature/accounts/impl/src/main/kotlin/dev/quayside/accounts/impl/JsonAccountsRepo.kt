package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountRules
import dev.quayside.accounts.api.AccountsError
import dev.quayside.accounts.api.AccountsRepo
import dev.quayside.accounts.api.AccountsState
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * [AccountsRepo] persisted in `accounts.json` through [JsonStore] (ADR 0035). Loaded once by [open]: a broken
 * file is moved aside by the store and the default account applies; a file from a newer version is never touched,
 * so every update is refused with [AccountsError.NewerSchema]. Each update runs under a lock, is validated before
 * the save and published only after the save succeeded. Logs carry fixed categories only (ENG-C8).
 */
internal class JsonAccountsRepo private constructor(
    private val store: JsonStore<AccountsState>,
    private val home: Path,
    private val logger: Logger,
    initial: AccountsState,
    private val newerSchema: Boolean,
) : AccountsRepo {
    private val current = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val state: StateFlow<AccountsState> = current.asStateFlow()

    override suspend fun update(change: (AccountsState) -> AccountsState): Outcome<AccountsState, AccountsError> {
        if (newerSchema) return Outcome.Err(AccountsError.NewerSchema)
        return mutex.withLock {
            val next = change(current.value)
            val problem = AccountRules.validate(next, home)
            if (problem != null) {
                Outcome.Err(AccountsError.Invalid(problem))
            } else {
                // Not cancellable: a cancellation after the atomic move must not leave file and state apart.
                withContext(NonCancellable) { save(next) }
            }
        }
    }

    private suspend fun save(next: AccountsState): Outcome<AccountsState, AccountsError> =
        when (store.save(next)) {
            is Outcome.Ok -> {
                current.value = next
                Outcome.Ok(next)
            }

            is Outcome.Err -> {
                logger.warn("accounts save failed")
                Outcome.Err(AccountsError.WriteFailed)
            }
        }

    internal companion object {
        const val FILE_NAME = "accounts.json"

        suspend fun open(
            store: JsonStore<AccountsState>,
            home: Path,
            logger: Logger,
        ): JsonAccountsRepo =
            when (val loaded = store.load()) {
                is Outcome.Ok -> {
                    if (loaded.value.recovered != null) logger.warn("accounts recovered from a broken file")
                    JsonAccountsRepo(store, home, logger, loaded.value.value, newerSchema = false)
                }

                is Outcome.Err -> {
                    val newer = loaded.error is StorageError.NewerSchema
                    logger.warn(if (newer) "accounts file is newer" else "accounts file unreadable")
                    JsonAccountsRepo(store, home, logger, defaultStateOf(home), newer)
                }
            }
    }
}
