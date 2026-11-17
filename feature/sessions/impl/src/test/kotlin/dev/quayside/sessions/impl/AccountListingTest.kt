package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Clock
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [AccountListing] without the polling loop: which time it reports when several accounts fail. */
class AccountListingTest {
    private val other = AccountId("other")

    private fun runtime(account: AccountId = AccountId.DEFAULT) =
        SwitchableRuntime(FakeAgentRuntime(RuntimeWorld(account = account)), { 0L })

    @Test
    fun `when several accounts fail the time shown is of the first one in order`() =
        runTest {
            var now = 0L
            val work = runtime(WORK).also { it.available(false) }
            val extra = MutableStateFlow<Map<AccountId, SwitchableRuntime>>(mapOf(WORK to work))
            val listing =
                AccountListing(
                    AccountRuntimes(runtime(), extra),
                    RecordingLogger(),
                    Clock { atMillis(now) },
                )
            listing.list().staleSince shouldBe atMillis(0)

            // The second extra account never answers either, but its first try is later than work's.
            now = 2_000
            extra.value = mapOf(WORK to work, other to runtime(other).also { it.available(false) })
            val listed = listing.list()

            listed.staleSince shouldBe atMillis(0)
            listed.staleBy shouldBe RuntimeError.NotInstalled
        }
}
