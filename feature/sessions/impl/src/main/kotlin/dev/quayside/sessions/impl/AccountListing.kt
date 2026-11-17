package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn

/** What [AccountListing.list] found: the [listing] to publish and the first error of an account that failed. */
internal data class AccountsListed(
    val listing: Outcome<List<RuntimeSession>, RuntimeError>,
    val staleBy: RuntimeError?,
)

/**
 * Lists every account and merges the results with [ListingMerge] (AM-1003). Keeps the last successful list of each
 * account for the one whose next listing fails. Not thread-safe: [SessionsState] calls it under its mutex.
 */
internal class AccountListing(
    private val runtimes: AccountRuntimes,
    private val logger: Logger,
) {
    private var cache: Map<AccountId, List<RuntimeSession>> = emptyMap()
    private var collisions = 0

    /**
     * When only some accounts failed the listing is `Ok` over their cached sessions and [AccountsListed.staleBy] is the
     * error of the first of them: the snapshot is stale, but polling does not back off. When every account failed the
     * listing is that first error.
     */
    suspend fun list(): AccountsListed {
        val results = runtimes.listAll()
        val merged = ListingMerge.merge(results, cache)
        cache = merged.cache
        logCollisions(merged.collisions)
        val first = merged.failed.values.firstOrNull()
        return if (first != null && merged.failed.size == results.size) {
            AccountsListed(Outcome.Err(first), null)
        } else {
            AccountsListed(Outcome.Ok(merged.sessions), first)
        }
    }

    /** A count only (ENG-C8), and only when it changes, so a standing collision does not log every 2 s. */
    private fun logCollisions(count: Int) {
        if (count == collisions) return
        collisions = count
        if (count > 0) logger.warn("Session ids collide between accounts", "count" to count)
    }
}
