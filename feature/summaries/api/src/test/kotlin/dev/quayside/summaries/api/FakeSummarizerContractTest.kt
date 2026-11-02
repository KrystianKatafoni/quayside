package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The fake passes the same contract as the real summarizer (ENG-T2). */
class FakeSummarizerContractTest : SummarizerContract() {
    override fun summarizer(): Summarizer = FakeSummarizer()

    @Test
    fun `scripted message gets its result and every call is recorded`() {
        val fake = FakeSummarizer(mapOf("boom" to Outcome.Err(SummaryError.Empty), "x" to Outcome.Ok("Short.")))

        fake.summarize("boom") shouldBe Outcome.Err(SummaryError.Empty)
        fake.summarize("x") shouldBe Outcome.Ok("Short.")
        fake.summarize("  two   words ") shouldBe Outcome.Ok("two words")

        fake.calls shouldBe listOf("boom", "x", "  two   words ")
    }
}
