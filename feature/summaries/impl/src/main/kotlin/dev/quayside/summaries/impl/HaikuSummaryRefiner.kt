package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.SummaryError
import dev.quayside.summaries.api.SummaryRefiner
import kotlin.time.Duration.Companion.seconds

/**
 * [SummaryRefiner] that asks the Haiku model through [prompt] (ADR 0019). Sends the last [MAX_MESSAGE] chars of
 * the message; the answer goes through [LastMessageSummary] so it keeps the contract of a summary. Errors are
 * fixed categories: no `claude` or no answer in time is [SummaryError.Unavailable], anything else
 * [SummaryError.Failed].
 */
internal class HaikuSummaryRefiner(
    private val prompt: OneShotPrompt,
) : SummaryRefiner {
    override suspend fun refine(lastMessage: String): Outcome<String, SummaryError> {
        if (lastMessage.isBlank()) return Outcome.Err(SummaryError.Empty)
        val request = PromptRequest(INSTRUCTION + tail(lastMessage), MODEL, TIMEOUT)
        return when (val answer = prompt.ask(request)) {
            is Outcome.Ok -> {
                LastMessageSummary.of(answer.value)?.let { Outcome.Ok(it) }
                    ?: Outcome.Err(SummaryError.Empty)
            }

            is Outcome.Err -> {
                Outcome.Err(answer.error.toSummaryError())
            }
        }
    }

    /** The last [MAX_MESSAGE] chars, without a lone low surrogate at the cut. */
    private fun tail(message: String): String {
        val cut = message.takeLast(MAX_MESSAGE)
        return if (cut.isNotEmpty() && cut.first().isLowSurrogate() && cut.length < message.length) cut.drop(1) else cut
    }

    private fun RuntimeError.toSummaryError(): SummaryError =
        when (this) {
            RuntimeError.NotInstalled, RuntimeError.Timeout -> SummaryError.Unavailable
            else -> SummaryError.Failed
        }

    private companion object {
        const val MODEL = "haiku"
        const val MAX_MESSAGE = 8_000
        val TIMEOUT = 30.seconds
        const val INSTRUCTION =
            "Summarize the message below in at most two short sentences, in the language of the message. " +
                "Answer with the summary only: plain text, one paragraph, no Markdown.\n\nMessage:\n"
    }
}
