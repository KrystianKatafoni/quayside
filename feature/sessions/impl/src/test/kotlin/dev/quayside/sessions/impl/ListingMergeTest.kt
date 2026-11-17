package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** [ListingMerge.merge]: the table of docs/plans/AM-1003.md ("D" = default account, "W" = account `work`). */
class ListingMergeTest {
    private val d = AccountId.DEFAULT
    private val w = AccountId("work")

    private fun session(
        id: String,
        account: AccountId,
    ): RuntimeSession = aRuntimeSession(id = SessionId(id), account = account)

    private val a = session("aaaa0001", d)
    private val b = session("bbbb0001", w)

    private fun ok(vararg sessions: RuntimeSession): Outcome<List<RuntimeSession>, RuntimeError> =
        Outcome.Ok(sessions.toList())

    private fun err(error: RuntimeError = RuntimeError.Timeout): Outcome<List<RuntimeSession>, RuntimeError> =
        Outcome.Err(error)

    @Test
    fun `two successful listings give the sessions of both accounts and no failure`() {
        val merged = ListingMerge.merge(mapOf(d to ok(a), w to ok(b)), emptyMap())

        merged.sessions shouldBe listOf(a, b)
        merged.failed shouldBe emptyMap()
        merged.collisions shouldBe 0
    }

    @Test
    fun `the account of the map key overrides a wrong stamp of the session`() {
        val wrong = b.copy(account = d)

        val merged = ListingMerge.merge(mapOf(d to ok(a), w to ok(wrong)), emptyMap())

        merged.sessions shouldBe listOf(a, b)
    }

    @Test
    fun `a failed account keeps its cached sessions and is reported`() {
        val merged =
            ListingMerge.merge(mapOf(d to ok(a), w to err()), cache = mapOf(w to listOf(b)))

        merged.sessions shouldBe listOf(a, b)
        merged.failed shouldBe mapOf(w to RuntimeError.Timeout)
    }

    @Test
    fun `a failed account without a cache has no sessions`() {
        val merged = ListingMerge.merge(mapOf(d to ok(a), w to err()), emptyMap())

        merged.sessions shouldBe listOf(a)
        merged.failed shouldBe mapOf(w to RuntimeError.Timeout)
    }

    @Test
    fun `a failed default account keeps its cached sessions while another account is fresh`() {
        val merged =
            ListingMerge.merge(mapOf(d to err(RuntimeError.NotInstalled), w to ok(b)), cache = mapOf(d to listOf(a)))

        merged.sessions shouldBe listOf(a, b)
        merged.failed shouldBe mapOf(d to RuntimeError.NotInstalled)
    }

    @Test
    fun `when every account fails the cached sessions stay and both failures are reported in account order`() {
        val merged =
            ListingMerge.merge(
                mapOf(d to err(RuntimeError.NotInstalled), w to err(RuntimeError.Timeout)),
                cache = mapOf(d to listOf(a), w to listOf(b)),
            )

        merged.sessions shouldBe listOf(a, b)
        merged.failed.keys.toList() shouldBe listOf(d, w)
        merged.failed.values.first() shouldBe RuntimeError.NotInstalled
    }

    @Test
    fun `the same id on two accounts keeps the session of the earlier account and counts the drop`() {
        val onDefault = session("cccc0001", d)
        val onWork = session("cccc0001", w)

        val merged = ListingMerge.merge(mapOf(d to ok(onDefault), w to ok(onWork)), emptyMap())

        merged.sessions shouldBe listOf(onDefault)
        merged.collisions shouldBe 1
    }

    @Test
    fun `every dropped session of a collision is counted`() {
        val x = session("cccc0001", d)
        val y = session("cccc0002", d)

        val merged =
            ListingMerge.merge(
                mapOf(d to ok(x, y), w to ok(session("cccc0001", w), session("cccc0002", w), b)),
                emptyMap(),
            )

        merged.sessions shouldBe listOf(x, y, b)
        merged.collisions shouldBe 2
    }

    @Test
    fun `an account missing from the results drops out of the sessions and the cache`() {
        val merged = ListingMerge.merge(mapOf(d to ok(a)), cache = mapOf(w to listOf(b)))

        merged.sessions shouldBe listOf(a)
        merged.failed shouldBe emptyMap()
        merged.cache.keys shouldBe setOf(d)
    }

    @Test
    fun `the new cache holds the fresh list of a good account and the old one of a failed account`() {
        val older = session("aaaa0009", d)

        val cache = mapOf(d to listOf(older), w to listOf(b))

        val merged = ListingMerge.merge(mapOf(d to ok(a), w to err()), cache)

        merged.cache shouldBe mapOf(d to listOf(a), w to listOf(b))
    }
}
