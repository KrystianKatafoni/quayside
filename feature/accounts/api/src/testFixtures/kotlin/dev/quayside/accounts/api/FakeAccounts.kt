package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-memory [AccountsRepo] and [AccountSetup] for tests of other modules. Passes [AccountsRepoContract] and
 * [AccountSetupContract]. Validation goes through the shared [AccountRules] like the real implementation.
 * Creates no directories; [register] only looks at the real file system to tell directories from files.
 */
public class FakeAccounts(
    private val home: Path,
) : AccountsRepo,
    AccountSetup {
    private val current = MutableStateFlow(defaultState(home))
    private val mutex = Mutex()
    private var failSave = false

    override val state: StateFlow<AccountsState> = current.asStateFlow()

    /** The next [update] fails with [AccountsError.WriteFailed] and changes nothing; later ones work. */
    public fun failNextSave() {
        failSave = true
    }

    override suspend fun update(change: (AccountsState) -> AccountsState): Outcome<AccountsState, AccountsError> =
        mutex.withLock {
            val next = change(current.value)
            val problem = AccountRules.validate(next, home)
            when {
                problem != null -> Outcome.Err(AccountsError.Invalid(problem))
                failSave -> Outcome.Err(AccountsError.WriteFailed).also { failSave = false }
                else -> Outcome.Ok(next).also { current.value = next }
            }
        }

    override suspend fun create(request: NewAccount): Outcome<Account, AccountSetupError> {
        val id = AccountRules.slug(request.label) ?: return Outcome.Err(AccountSetupError.InvalidName)
        return append(anAccount(id.value, home, request.label))
    }

    override suspend fun register(
        label: String,
        dir: Path,
    ): Outcome<Account, AccountSetupError> {
        val id = AccountRules.slug(label) ?: return Outcome.Err(AccountSetupError.InvalidName)
        val error =
            when {
                !Files.isDirectory(dir) -> AccountSetupError.NotADirectory
                dir.toRealPath() == defaultAccount(home).configDir.toRealPath() -> AccountSetupError.DefaultDirectory
                else -> null
            }
        return error?.let { Outcome.Err(it) } ?: append(Account(id, label, dir.toAbsolutePath().normalize()))
    }

    override suspend fun copyFromDefault(
        id: AccountId,
        items: Set<CopyItem>,
    ): Outcome<CopyReport, AccountSetupError> =
        when {
            id == AccountId.DEFAULT -> Outcome.Err(AccountSetupError.DefaultDirectory)
            current.value.accounts.none { it.id == id } -> Outcome.Err(AccountSetupError.UnknownAccount)
            else -> Outcome.Ok(CopyReport(items, emptySet(), null))
        }

    private suspend fun append(account: Account): Outcome<Account, AccountSetupError> {
        val taken = current.value.accounts.any { it.id == account.id || it.configDir == account.configDir }
        if (taken) return Outcome.Err(AccountSetupError.AlreadyExists)
        return when (val result = update { it.copy(accounts = it.accounts + account) }) {
            is Outcome.Ok -> Outcome.Ok(account)
            is Outcome.Err -> Outcome.Err(AccountSetupError.Registry(result.error))
        }
    }
}
