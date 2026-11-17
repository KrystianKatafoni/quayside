package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId

/**
 * Result of [ListingMerge.merge].
 *
 * @property sessions sessions of every account in order of the accounts, each stamped with its account.
 * @property failed accounts whose listing failed, with the error, in order of the accounts.
 * @property cache last successful list of each account still present: the cache of the next merge.
 * @property collisions how many sessions were dropped because an earlier account reported the same id.
 */
internal data class MergedListing(
    val sessions: List<RuntimeSession>,
    val failed: Map<AccountId, RuntimeError>,
    val cache: Map<AccountId, List<RuntimeSession>>,
    val collisions: Int,
)

/** Pure merge of the listings of many accounts (AM-1003): see docs/plans/AM-1003.md for the rules. */
internal object ListingMerge {
    /** [results] in order of the accounts (the default one first); [cache] holds the last successful lists. */
    fun merge(
        results: Map<AccountId, Outcome<List<RuntimeSession>, RuntimeError>>,
        cache: Map<AccountId, List<RuntimeSession>>,
    ): MergedListing {
        val lists =
            results.mapValues { (account, result) ->
                val listed = (result as? Outcome.Ok)?.value ?: cache[account].orEmpty()
                listed.map { it.copy(account = account) }
            }
        val failed = results.mapNotNull { (account, result) -> (result as? Outcome.Err)?.let { account to it.error } }
        val seen = mutableSetOf<SessionId>()
        var collisions = 0
        val sessions =
            lists.values.flatMap { list ->
                val (kept, dropped) = list.partition { it.id !in seen }
                seen += kept.map { it.id }
                collisions += dropped.size
                kept
            }
        return MergedListing(sessions, failed.toMap(), lists, collisions)
    }
}
