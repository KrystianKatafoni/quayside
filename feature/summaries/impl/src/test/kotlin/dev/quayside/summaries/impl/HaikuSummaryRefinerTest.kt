package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.Summarizer
import dev.quayside.summaries.api.SummaryError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/** The model summary (ADR 0019) over [FakeOneShotPrompt]: what is asked, and what comes back. */
class HaikuSummaryRefinerTest {
    private fun refiner(prompt: FakeOneShotPrompt) = HaikuSummaryRefiner(prompt)

    private fun answering(text: String) = FakeOneShotPrompt { Outcome.Ok(text) }

    private fun failing(error: RuntimeError) = FakeOneShotPrompt { Outcome.Err(error) }

    @Test
    fun `asks haiku with the last 8000 chars`() =
        runTest {
            val prompt = FakeOneShotPrompt()
            val message = "§".repeat(1_000) + "¶".repeat(8_000)

            refiner(prompt).refine(message)

            val request = prompt.calls.single()
            request.model shouldBe "haiku"
            request.timeout shouldBe 30.seconds
            request.prompt shouldContain "¶".repeat(8_000)
            request.prompt shouldNotContain "¶".repeat(8_001)
            request.prompt shouldNotContain "§"
        }

    @Test
    fun `a short message goes to the model whole`() =
        runTest {
            val prompt = FakeOneShotPrompt()

            refiner(prompt).refine("Fixed the login form.\nAdded a test.")

            prompt.calls.single().prompt shouldContain "Fixed the login form.\nAdded a test."
        }

    @Test
    fun `the cut of a long message does not split a surrogate pair`() =
        runTest {
            val prompt = FakeOneShotPrompt()
            // 10002 chars: the 8000 char tail starts on the low half of a pair.
            val message = "a" + "😀".repeat(5_000) + "b"

            refiner(prompt).refine(message)

            val text = prompt.calls.single().prompt
            String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8) shouldBe text
            text shouldContain "😀b"
        }

    @Test
    fun `answer is cleaned to one line`() =
        runTest {
            val answer = "## Title\n\n**Fixed** the form.\nSecond line. Third one. Fourth."

            refiner(answering(answer)).refine("message") shouldBe Outcome.Ok("Fixed the form. Second line.")
        }

    @Test
    fun `a long answer is cut to the summary limit`() =
        runTest {
            val summary = refiner(answering("word ".repeat(100))).refine("message")

            val text = summary.shouldBeInstanceOf<Outcome.Ok<String>>().value
            text.codePointCount(0, text.length) shouldBeLessThanOrEqual Summarizer.MAX_LENGTH
        }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\n\n", "```\nonly code\n```", "| a | b |"])
    fun `blank answer gives Empty`(answer: String) =
        runTest {
            refiner(answering(answer)).refine("message") shouldBe Outcome.Err(SummaryError.Empty)
        }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\n\t \r\n"])
    fun `blank message gives Empty without asking the model`(message: String) =
        runTest {
            val prompt = FakeOneShotPrompt()

            refiner(prompt).refine(message) shouldBe Outcome.Err(SummaryError.Empty)

            prompt.calls.shouldBeEmpty()
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errors")
    fun `model errors map to fixed categories`(
        error: RuntimeError,
        expected: SummaryError,
    ) = runTest {
        refiner(failing(error)).refine("message") shouldBe Outcome.Err(expected)
    }

    @Test
    fun `timeout maps to Unavailable`() =
        runTest {
            refiner(failing(RuntimeError.Timeout)).refine("message") shouldBe Outcome.Err(SummaryError.Unavailable)
        }

    @Test
    fun `an error carries nothing of the message or the stderr`() =
        runTest {
            val error = RuntimeError.CommandFailed(1, "cannot run 'confidential message'")

            val result = refiner(failing(error)).refine("confidential message")

            result.toString() shouldNotContain "confidential"
        }

    @Test
    fun `the request is valid for the port`() =
        runTest {
            val prompt = FakeOneShotPrompt()

            refiner(prompt).refine("x".repeat(100_000))

            prompt.calls
                .single()
                .prompt.length shouldBeLessThanOrEqual PromptRequest.MAX_PROMPT
        }

    companion object {
        @JvmStatic
        fun errors(): Stream<Arguments> =
            Stream.of(
                Arguments.of(RuntimeError.NotInstalled, SummaryError.Unavailable),
                Arguments.of(RuntimeError.Timeout, SummaryError.Unavailable),
                Arguments.of(RuntimeError.CommandFailed(1, "boom"), SummaryError.Failed),
                Arguments.of(RuntimeError.Unparseable("print result"), SummaryError.Failed),
                Arguments.of(RuntimeError.AgentViewDisabled, SummaryError.Failed),
                Arguments.of(RuntimeError.UnsupportedVersion("2.0.0", "2.1.139"), SummaryError.Failed),
            )
    }
}
