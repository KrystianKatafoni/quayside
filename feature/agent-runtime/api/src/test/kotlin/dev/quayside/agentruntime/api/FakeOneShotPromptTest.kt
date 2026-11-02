package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** What tests of other features rely on in [FakeOneShotPrompt]: recorded calls and the count of running ones. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeOneShotPromptTest {
    private fun request(prompt: String) = PromptRequest(prompt, "haiku", 30.seconds)

    @Test
    fun `records every request in call order`() =
        runTest {
            val fake = FakeOneShotPrompt()
            fake.calls.shouldBeEmpty()

            fake.ask(request("one"))
            fake.ask(request("two"))

            fake.calls.map { it.prompt } shouldBe listOf("one", "two")
        }

    @Test
    fun `counts calls running at the same time`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fake = FakeOneShotPrompt { gate.await().let { Outcome.Ok("done") } }

            val first = launch { fake.ask(request("one")) }
            val second = launch { fake.ask(request("two")) }
            runCurrent()
            fake.active shouldBe 2

            gate.complete(Unit)
            runCurrent()

            first.isCompleted shouldBe true
            second.isCompleted shouldBe true
            fake.active shouldBe 0
            fake.maxActive shouldBe 2
        }

    @Test
    fun `a cancelled call is no longer active`() =
        runTest {
            val fake = FakeOneShotPrompt { CompletableDeferred<Outcome<String, RuntimeError>>().await() }

            val job = launch { fake.ask(request("one")) }
            runCurrent()
            fake.active shouldBe 1

            job.cancel()
            runCurrent()

            fake.active shouldBe 0
        }
}
