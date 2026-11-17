package dev.quayside.app

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountsState
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.impl.agentRuntimeModule
import dev.quayside.core.domain.AccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * The runtimes of the accounts other than the default one, built from [accounts] (AM-1003): one per account in the
 * order of the file, also for a disabled one (ADR 0029). A runtime is replaced only when the `configDir` of its account
 * changes, so renaming or switching an account keeps the instance. The first value is there at once; later changes
 * are followed in [scope].
 *
 * @param build creates the runtime of an account from its id and `configDir`.
 */
internal class AccountRuntimeRegistry(
    accounts: StateFlow<AccountsState>,
    scope: CoroutineScope,
    private val build: (AccountId, Path) -> AgentRuntime,
) {
    /** The runtime of each account with the `configDir` it was built for; touched by [refresh] only. */
    private var built: Map<AccountId, Pair<Path, AgentRuntime>> = emptyMap()
    private val current = MutableStateFlow(refresh(accounts.value))

    /** One runtime per account except the default one, in the order of the file. */
    val runtimes: StateFlow<Map<AccountId, AgentRuntime>> = current.asStateFlow()

    init {
        scope.launch { accounts.collect { current.value = refresh(it) } }
    }

    /** Keeps the runtime of an account whose `configDir` is unchanged; builds the others. */
    private fun refresh(state: AccountsState): Map<AccountId, AgentRuntime> {
        built =
            state.extraAccounts().associate { account ->
                val kept = built[account.id]?.takeIf { it.first == account.configDir }
                account.id to (kept ?: (account.configDir to build(account.id, account.configDir)))
            }
        return OrderedMap(built.mapValues { (_, entry) -> entry.second })
    }
}

private fun AccountsState.extraAccounts(): List<Account> = accounts.filter { it.id != AccountId.DEFAULT }

/**
 * A map that is equal to another one only with the same entries in the same order: a `StateFlow` of it then also
 * publishes a reordering of the accounts, which decides the order of the listings (AM-1003).
 */
private class OrderedMap<K, V>(
    private val map: Map<K, V>,
) : Map<K, V> by map {
    override fun equals(other: Any?): Boolean =
        other is OrderedMap<*, *> && map.entries.toList() == other.map.entries.toList()

    override fun hashCode(): Int = map.entries.toList().hashCode()

    override fun toString(): String = map.toString()
}

/** The supervisor of the default account and those of the other accounts (AM-1003). */
internal class Supervisors(
    val default: AgentRuntime,
    val extra: StateFlow<Map<AccountId, AgentRuntime>>,
)

/** The supervisor of an account other than the default one: the same adapter under `CLAUDE_CONFIG_DIR` (AM-1003). */
internal fun accountRuntime(
    core: CoreDeps,
    account: AccountId,
    configDir: Path,
): AgentRuntime =
    agentRuntimeModule(
        core.process,
        core.clock,
        core.logger,
        core.homeDir,
        injectHookToken = true,
        account = account,
        configDir = configDir,
    )
