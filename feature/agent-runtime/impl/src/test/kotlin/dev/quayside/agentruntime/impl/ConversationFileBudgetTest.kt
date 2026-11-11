package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.aConversationMessage
import dev.quayside.agentruntime.impl.ConversationFileKit.messages
import dev.quayside.agentruntime.impl.ConversationFileKit.ok
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The byte budget of one call is spent also inside a single very long line (AM-315, ADR 0034, decision 3): such a
 * line is not read to its end, and the next call with the cursor goes on without a gap or a duplicate.
 */
class ConversationFileBudgetTest {
    @TempDir
    lateinit var dir: Path

    private val id = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"

    private fun file(text: String): Path =
        dir.resolve("$id.jsonl").also { Files.write(it, text.toByteArray(Charsets.UTF_8)) }

    @Test
    fun `reader stops inside a long line within the budget and a second reader goes on from there`() {
        val text = "a\n${"y".repeat(10_000)}\nb\n"
        val file = file(text)
        val end = Files.size(file)

        val first = BackwardLineReader(file, end, CHUNK, MAX_LINE, BUDGET)
        first.next()?.text shouldBe "b"
        first.next() shouldBe null

        // the budget counts from the end; the stop lies in the last chunk that was started after it was spent
        (end - first.boundary >= BUDGET) shouldBe true
        (end - first.boundary <= BUDGET + CHUNK) shouldBe true
        val seen = mutableListOf("b")
        var cursor = first.boundary
        while (cursor > 0) {
            val reader = BackwardLineReader(file, cursor, CHUNK, MAX_LINE, BUDGET)
            generateSequence(reader::next).forEach { seen.add(it.text) }
            cursor = if (reader.boundary < cursor) reader.boundary else error("no progress at $cursor")
        }
        seen shouldBe listOf("b", "a")
    }

    @Test
    fun `a line of 20 MiB is not read to its end and the pages still add up without a duplicate`() {
        val old = messages(2)
        val newest = aConversationMessage(3)
        val huge = fillerLine(20 * MIB)
        val text =
            conversationText(id, "/p", old) + huge + "\n" + conversationText(id, "/p", listOf(newest))
        val file = file(text)
        val size = Files.size(file)

        val first = ConversationFile.page(file, null, PAGE).ok()

        // one call does not get past the budget plus one chunk: the cursor lies inside the 20 MiB line
        first.messages shouldBe listOf(newest)
        val cursor = checkNotNull(first.older).offset
        (size - cursor >= ConversationFile.BUDGET_BYTES) shouldBe true
        (size - cursor <= ConversationFile.BUDGET_BYTES + BackwardLines.CHUNK_BYTES) shouldBe true
        everything(file) shouldBe old + newest
    }

    @Test
    fun `a short line that lasts past the budget is read to its end and its message is not lost`() {
        val file = file("a\n${"m".repeat(80)}\n${"n".repeat(60)}\n")
        val seen = mutableListOf<String>()
        var cursor = Files.size(file)
        var guard = 0
        while (cursor > 0 && guard++ < 50) {
            val reader = BackwardLineReader(file, cursor, CHUNK, BackwardLines.MAX_LINE_BYTES, BUDGET)
            generateSequence(reader::next).forEach { seen.add(it.text.take(1)) }
            cursor = reader.boundary
        }
        // the budget is spent inside "m" (80 bytes < the line limit): it is read on, not skipped
        seen shouldBe listOf("n", "m", "a")
    }

    private fun everything(file: Path): List<ConversationMessage> {
        val pages = mutableListOf<List<ConversationMessage>>()
        var cursor: HistoryCursor? = null
        do {
            val page = ConversationFile.page(file, cursor, PAGE).ok()
            pages.add(0, page.messages)
            val older = page.older
            check(older == null || cursor == null || older.offset < cursor.offset) { "no progress" }
            if (page.messages.isEmpty() && older != null && cursor != null) {
                // an empty page inside a long line: one call reads at most the budget plus one chunk
                val read = cursor.offset - older.offset
                (read <= ConversationFile.BUDGET_BYTES + BackwardLines.CHUNK_BYTES) shouldBe true
            }
            cursor = older
        } while (cursor != null)
        return pages.flatten()
    }

    private companion object {
        const val CHUNK = 16
        const val MAX_LINE = 20
        const val BUDGET = 100L
        const val PAGE = 20
        const val MIB = 1024 * 1024
    }
}
