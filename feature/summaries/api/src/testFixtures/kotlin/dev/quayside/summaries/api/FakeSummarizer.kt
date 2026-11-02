package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome

/**
 * [Summarizer] for tests of code that shows summaries (ENG-I7). Passes [SummarizerContract].
 *
 * A message with an entry in [scripted] gets that result; any other message gets all its words on one line,
 * cut to [Summarizer.MAX_LENGTH] code points, or [SummaryError.Empty] when it has no words. [calls] keeps the
 * messages in the order they came in.
 */
public class FakeSummarizer(
    private val scripted: Map<String, Outcome<String, SummaryError>> = emptyMap(),
) : Summarizer {
    private val received = mutableListOf<String>()

    /** Messages passed to [summarize] so far. */
    public val calls: List<String> get() = synchronized(received) { received.toList() }

    override fun summarize(lastMessage: String): Outcome<String, SummaryError> {
        synchronized(received) { received += lastMessage }
        return scripted[lastMessage] ?: oneLine(lastMessage)
    }

    private fun oneLine(message: String): Outcome<String, SummaryError> {
        val line = message.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (line.isEmpty()) return Outcome.Err(SummaryError.Empty)
        val end = if (line.codePointCount(0, line.length) <= Summarizer.MAX_LENGTH) line.length else cutAt(line)
        return Outcome.Ok(line.substring(0, end))
    }

    private fun cutAt(line: String): Int = line.offsetByCodePoints(0, Summarizer.MAX_LENGTH)
}
