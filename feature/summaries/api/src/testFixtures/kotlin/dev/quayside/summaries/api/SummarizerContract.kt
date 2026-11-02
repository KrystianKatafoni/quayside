package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** Behaviour every [Summarizer] must have (ENG-T2). Runs on the fake and on the real summarizer. */
public abstract class SummarizerContract {
    /** The summarizer under test. */
    protected abstract fun summarizer(): Summarizer

    private fun ok(message: String): String =
        summarizer().summarize(message).shouldBeInstanceOf<Outcome.Ok<String>>().value

    @Test
    public fun `blank message gives Empty`() {
        listOf("", " ", "  \n\t \r\n ").forEach { message ->
            summarizer().summarize(message) shouldBe Outcome.Err(SummaryError.Empty)
        }
    }

    @Test
    public fun `summary is a single non blank line`() {
        val summary = ok("Fixed the login form.\n\nAdded a test.\r\nAll green now.")

        summary.shouldNotBeBlank()
        summary shouldNotContain "\n"
        summary shouldNotContain "\r"
    }

    @Test
    public fun `summary is at most MAX_LENGTH code points`() {
        val words = "word ".repeat(100).trim()
        val emoji = "😀 ".repeat(150).trim()

        listOf(words, emoji, "a".repeat(400)).forEach { message ->
            val summary = ok(message)
            summary.codePointCount(0, summary.length) shouldBeLessThanOrEqual Summarizer.MAX_LENGTH
        }
    }

    @Test
    public fun `short plain sentence is kept as is`() {
        ok("Fixed the login form.") shouldBe "Fixed the login form."
    }
}
