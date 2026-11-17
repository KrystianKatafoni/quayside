@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrNull
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** [AccountRuntimes]: which runtimes exist and how they are listed (AM-1003). */
class AccountRuntimesTest {
    private val work = AccountId("work")
    private val personal = AccountId("personal")
    private val defaultFake = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session("aaaa0001"))))
    private val workFake = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session("bbbb0001")), account = work))

    private fun session(id: String): RuntimeSession = aRuntimeSession(id = SessionId(id))

    @Test
    fun `lists every account in parallel`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val first = GatedRuntime(defaultFake, gate)
            val second = GatedRuntime(workFake, gate = null)
            val runtimes = AccountRuntimes(first, MutableStateFlow(mapOf(work to second)))

            val listing = async { runtimes.listAll() }
            runCurrent()

            // The default account waits for the gate, and the other account is already called.
            first.started shouldBe true
            second.started shouldBe true
            listing.isCompleted shouldBe false

            gate.complete(Unit)
            val result = listing.await()
            result.keys.toList() shouldBe listOf(AccountId.DEFAULT, work)
            result.getValue(work).shouldBeOkWith(SessionId("bbbb0001"))
            result.getValue(AccountId.DEFAULT).shouldBeOkWith(SessionId("aaaa0001"))
        }

    @Test
    fun `one failing account does not hide the other one`() =
        runTest {
            workFake.failNext(RuntimeError.Timeout)
            val runtimes = AccountRuntimes(defaultFake, MutableStateFlow(mapOf(work to workFake)))

            val result = runtimes.listAll()

            result.getValue(work) shouldBe Outcome.Err(RuntimeError.Timeout)
            result.getValue(AccountId.DEFAULT).shouldBeOkWith(SessionId("aaaa0001"))
        }

    @Test
    fun `the default runtime comes first and the others keep the order of the map`() {
        val third = FakeAgentRuntime(RuntimeWorld(account = personal))
        val runtimes = AccountRuntimes(defaultFake, MutableStateFlow(linkedMapOf(personal to third, work to workFake)))

        runtimes.all.keys.toList() shouldBe listOf(AccountId.DEFAULT, personal, work)
        runtimes.of(personal) shouldBe third
        runtimes.of(work) shouldBe workFake
    }

    @Test
    fun `a DEFAULT key in extra runtimes is ignored`() =
        runTest {
            val impostor = FakeAgentRuntime(RuntimeWorld())
            val runtimes =
                AccountRuntimes(defaultFake, MutableStateFlow(mapOf(AccountId.DEFAULT to impostor, work to workFake)))

            runtimes.all.keys.toList() shouldBe listOf(AccountId.DEFAULT, work)
            runtimes.of(AccountId.DEFAULT) shouldBe defaultFake
            runtimes.listAll()
            impostor.calls.shouldBeEmpty()
        }

    @Test
    fun `an account missing from the map has no runtime`() {
        val runtimes = AccountRuntimes(defaultFake, MutableStateFlow(mapOf(work to workFake)))

        runtimes.of(AccountId("gone")).shouldBeNull()
    }

    @Test
    fun `a change of the map is visible at once`() {
        val extra = MutableStateFlow(emptyMap<AccountId, AgentRuntime>())
        val runtimes = AccountRuntimes(defaultFake, extra)
        runtimes.of(work).shouldBeNull()

        extra.value = mapOf(work to workFake)

        runtimes.of(work) shouldBe workFake
        runtimes.all.keys.toList() shouldBe listOf(AccountId.DEFAULT, work)

        extra.value = emptyMap()

        runtimes.of(work).shouldBeNull()
    }

    private fun Outcome<List<RuntimeSession>, RuntimeError>.shouldBeOkWith(id: SessionId) {
        getOrNull().shouldNotBeNull().map { it.id } shouldBe listOf(id)
    }

    /** A runtime whose listing records that it started and waits for [gate]. */
    private class GatedRuntime(
        private val fake: FakeAgentRuntime,
        private val gate: CompletableDeferred<Unit>?,
    ) : AgentRuntime by fake {
        var started = false

        override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> {
            started = true
            gate?.await()
            return fake.listSessions(under)
        }
    }
}
