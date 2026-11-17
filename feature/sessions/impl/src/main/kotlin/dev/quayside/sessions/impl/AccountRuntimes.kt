package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The supervisors of the accounts (AM-1003).
 *
 * @param default the runtime of [AccountId.DEFAULT], always present.
 * @param extra runtimes of the other accounts in automation order; a [AccountId.DEFAULT] key is ignored.
 */
internal class AccountRuntimes(
    private val default: AgentRuntime,
    private val extra: StateFlow<Map<AccountId, AgentRuntime>>,
) {
    /** The current runtimes, the default account first, then [extra] in its order. */
    val all: Map<AccountId, AgentRuntime>
        get() = mapOf(AccountId.DEFAULT to default) + extra.value.filterKeys { it != AccountId.DEFAULT }

    /** Emits the map of the extra accounts, now and at every change. */
    val changes: Flow<Map<AccountId, AgentRuntime>> get() = extra

    /** The runtime of [account], or null when the account has none. */
    fun of(account: AccountId): AgentRuntime? = all[account]

    /** Lists every account at the same time; the result is in the order of [all]. */
    suspend fun listAll(): Map<AccountId, Outcome<List<RuntimeSession>, RuntimeError>> =
        coroutineScope {
            all
                .mapValues { (_, runtime) -> async { runtime.listSessions(under = null) } }
                .mapValues { (_, listing) -> listing.await() }
        }
}
