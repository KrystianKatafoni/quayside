package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.Summarizer
import dev.quayside.summaries.api.SummarizerContract
import kotlinx.coroutines.runBlocking

/**
 * [HaikuSummaryRefiner] passes the [Summarizer] contract too (ENG-T2), through a test adapter: the model
 * repeats the message, so the contract judges what the refiner does with an answer.
 */
class HaikuSummaryRefinerContractTest : SummarizerContract() {
    @Suppress("ForbiddenMethodCall") // the contract is not suspending; runBlocking is allowed in tests
    override fun summarizer(): Summarizer {
        var repeated = ""
        val refiner = HaikuSummaryRefiner(FakeOneShotPrompt { Outcome.Ok(repeated) })
        return Summarizer { message ->
            repeated = message
            runBlocking { refiner.refine(message) }
        }
    }
}
