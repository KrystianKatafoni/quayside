package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationPage
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.core.domain.Outcome
import java.nio.file.Files
import java.nio.file.Path

/** One page of the messages of a session file, read from the end (ADR 0034). */
internal object ConversationFile {
    const val BUDGET_BYTES: Long = 8L * 1024 * 1024
    private const val MAX_LIMIT = 100

    /**
     * Up to [limit] (coerced to 1..100) messages before [before] (null = the end of [file]), oldest first, and the
     * cursor of the older page (null at the start of the file). Reads at most [BUDGET_BYTES] backwards (a line over
     * 1 MiB is skipped, and once the budget is spent the read stops inside such a line instead of reading it to
     * its end); then the page holds what was found, with a cursor to go on (it may lie inside a long line; the next
     * call skips that partial line). A cursor outside the file is `UNREADABLE`. Throws `IOException`; the caller
     * maps it.
     */
    fun page(
        file: Path,
        before: HistoryCursor?,
        limit: Int,
    ): Outcome<ConversationPage, ConversationError> {
        val size = Files.size(file)
        val end = before?.offset ?: size
        if (end < 0 || end > size) return Outcome.Err(ConversationError.UNREADABLE)
        val reader =
            BackwardLineReader(file, end, BackwardLines.CHUNK_BYTES, BackwardLines.MAX_LINE_BYTES, BUDGET_BYTES)
        val found = ArrayDeque<Pair<Long, ConversationMessage>>()
        val wanted = limit.coerceIn(1, MAX_LIMIT)
        while (found.size < wanted) {
            val line = reader.next() ?: break
            ConversationEntries.messageOf(line.text)?.let { found.addFirst(line.offset to it) }
        }
        // the boundary is the start of the oldest message once the page is full, so one rule fits both endings
        val next = reader.boundary
        return Outcome.Ok(ConversationPage(found.map { it.second }, next.takeIf { it > 0 }?.let(::HistoryCursor)))
    }
}
