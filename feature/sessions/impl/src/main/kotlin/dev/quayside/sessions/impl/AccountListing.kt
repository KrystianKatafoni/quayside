package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import java.time.Instant

/**
 * What [AccountListing.list] found: the [listing] to publish, the first error of an account that failed
 * ([staleBy]) and when that account last listed successfully, or was first tried if it never did ([staleSince]; null
 * when every account failed or none did).
 */
internal data class AccountsListed(
    val listing: Outcome<List<RuntimeSession>, RuntimeError>,
    val staleBy: RuntimeError?,
    val staleSince: Instant? = null,
)

/**
 * Lists every account and merges the results with [ListingMerge] (AM-1003). Keeps the last successful list of each
 * account for the one whose next listing fails. Not thread-safe: [SessionsState] calls it under its mutex.
 */
internal class AccountListing(
    private val runtimes: AccountRuntimes,
    private val logger: Logger,
    private val clock: Clock,
) {
    private var freshAt: Map<AccountId, Instant> = emptyMap()
    private var cache: Map<AccountId, List<RuntimeSession>> = emptyMap()
    private var collisions = 0

    /**
     * When only some accounts failed the listing is `Ok` over their cached sessions and [AccountsListed.staleBy] is the
     * error of the first of them (with [AccountsListed.staleSince], its last good listing, or its first try if it
     * never answered): the snapshot is stale, but
     * polling does not back off. When every account failed the listing is that first error.
     */
    suspend fun list(): AccountsListed {
        val results = runtimes.listAll()
        val merged = ListingMerge.merge(results, cache)
        cache = merged.cache
        val now = clock.now()
        val ok = results.keys - merged.failed.keys
        val firstTry =
            merged.failed.keys
                .filter { it !in freshAt }
                .associateWith { now }
        freshAt = freshAt.filterKeys { it in merged.failed } + firstTry + ok.associateWith { now }
        logCollisions(merged.collisions)
        val first = merged.failed.values.firstOrNull()
        val since =
            merged.failed.keys
                .firstOrNull()
                ?.let { freshAt[it] }
        return if (first != null && merged.failed.size == results.size) {
            AccountsListed(Outcome.Err(first), null, null)
        } else {
            AccountsListed(Outcome.Ok(merged.sessions), first, since)
        }
    }

    /** A count only (ENG-C8), and only when it changes, so a standing collision does not log every 2 s. */
    private fun logCollisions(count: Int) {
        if (count == collisions) return
        collisions = count
        if (count > 0) logger.warn("Session ids collide between accounts", "count" to count)
    }
}
