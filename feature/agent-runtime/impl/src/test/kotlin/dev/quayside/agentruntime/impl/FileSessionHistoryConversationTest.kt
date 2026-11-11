package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationRole
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aConversationMessage
import dev.quayside.agentruntime.api.aPastConversation
import dev.quayside.agentruntime.impl.ConversationFileKit.everything
import dev.quayside.agentruntime.impl.ConversationFileKit.messages
import dev.quayside.agentruntime.impl.ConversationFileKit.ok
import dev.quayside.agentruntime.impl.ConversationFileKit.page
import dev.quayside.agentruntime.impl.ConversationFileKit.quayside
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * `FileSessionHistory.conversation` on files in a temporary directory (AM-315, ADR 0034): pages, cursors, fixtures.
 * Guards (ids, paths, limits, logs) are in [FileSessionHistoryConversationGuardsTest]. No real `~/.claude`.
 */
class FileSessionHistoryConversationTest {
    @TempDir
    lateinit var home: Path

    private val logger = RecordingLogger()
    private val conv = aPastConversation(aConversationId(SESSION_ID), startCwd = quayside, modifiedAt = T0)

    private fun TestScope.history(): SessionHistory =
        sessionHistoryModule(home, StandardTestDispatcher(testScheduler), logger)

    private fun long(n: Int): ConversationMessage = aConversationMessage(n, text = "m$n " + "w".repeat(KILOBYTE))

    @Test
    fun `a conversation over several chunks is read in pages that add up to the whole`() =
        runTest {
            // Given 300 messages of about 1 KiB: a file of over 300 KiB, five default chunks of 64 KiB
            val all = (1..300).map(::long)
            val file = writeConversationFile(home, conv, all)
            (Files.size(file) > 300 * KILOBYTE) shouldBe true

            // When paging from the end until the start
            val first = history().page().ok()

            // Then the first page is the newest 20, and every page together is the conversation, oldest first
            first.messages shouldBe all.takeLast(20)
            history().everything() shouldBe all
        }

    @Test
    fun `an older page continues before the cursor without overlap or gap`() =
        runTest {
            val all = (1..70).map(::long)
            writeConversationFile(home, conv, all)

            val first = history().page(limit = 30).ok()
            val second = history().page(before = first.older, limit = 30).ok()
            val third = history().page(before = second.older, limit = 30).ok()

            first.messages shouldBe all.subList(40, 70)
            second.messages shouldBe all.subList(10, 40)
            third.messages shouldBe all.subList(0, 10)
            third.older shouldBe null
        }

    @Test
    fun `the cursor is the byte offset of the line of the oldest message of the page`() =
        runTest {
            // Given a file that starts with a line that is not a message
            val head = """{"type":"last-prompt","sessionId":"$SESSION_ID"}""" + "\n"
            val body = conversationText(SESSION_ID, quayside.toString(), messages(3))
            val file = writeRaw(home, quayside, SESSION_ID, head + body, T0)
            val offsets = ConversationFileKit.lineOffsets(file)

            // When reading one message at a time
            val newest = history().page(limit = 1).ok()
            val middle = history().page(before = newest.older, limit = 1).ok()

            // Then each cursor is where the line of the oldest message of that page starts
            newest.older shouldBe HistoryCursor(offsets[3])
            middle.older shouldBe HistoryCursor(offsets[2])
        }

    @Test
    fun `cursor at the first message gives an empty final page`() =
        runTest {
            val head = """{"type":"last-prompt","sessionId":"$SESSION_ID"}""" + "\n"
            val body = conversationText(SESSION_ID, quayside.toString(), messages(2))
            val file = writeRaw(home, quayside, SESSION_ID, head + body, T0)
            val firstMessage = ConversationFileKit.lineOffsets(file)[1]

            val page = history().page(before = HistoryCursor(firstMessage)).ok()

            page.messages shouldBe emptyList()
            page.older shouldBe null
        }

    @Test
    fun `byte budget ends the page with a cursor and the next call goes on`() =
        runTest {
            // Given 3 old messages, 90 lines of 100 KiB (over the 8 MiB of one call) and the newest message
            val old = messages(3)
            val newest = aConversationMessage(4)
            val filler = (1..90).joinToString("") { fillerLine(100 * KILOBYTE) + "\n" }
            val text =
                conversationText(SESSION_ID, quayside.toString(), old) + filler +
                    conversationText(SESSION_ID, quayside.toString(), listOf(newest))
            writeRaw(home, quayside, SESSION_ID, text, T0)

            // When the first page is asked for
            val first = history().page().ok()

            // Then it holds what was found before the budget ended, with a cursor to go on
            first.messages shouldBe listOf(newest)
            (first.older != null) shouldBe true
            // And going on reaches the old messages
            history().everything() shouldBe old + newest
        }

    @Test
    fun `conversation with several cwds lists messages from all of them`() =
        runTest {
            ConversationFileKit.place(home, "cwd-changes.jsonl")

            val result = history().page().ok().messages

            result.map { it.role to it.text } shouldBe
                listOf(
                    ConversationRole.USER to "Start in the project root",
                    ConversationRole.ASSISTANT to "redacted",
                    ConversationRole.USER to "Now work in the subfolder",
                    ConversationRole.ASSISTANT to "redacted",
                )
        }

    @Test
    fun `file is found by the start directory after a move to a subfolder`() =
        runTest {
            ConversationFileKit.place(home, "cwd-changes.jsonl")

            (history().page(cwd = quayside) is Outcome.Ok) shouldBe true
            history().page(cwd = quayside.resolve("soft")) shouldBe Outcome.Err(ConversationError.NOT_FOUND)
        }

    @Test
    fun `start directory longer than 200 characters finds the file`() =
        runTest {
            val deep = Path.of("/" + "a".repeat(250) + "/project")
            val longConv = aPastConversation(aConversationId(SESSION_ID), startCwd = deep, modifiedAt = T0)
            writeConversationFile(home, longConv, messages(2))

            history().page(cwd = deep).ok().messages shouldBe messages(2)
        }

    @Test
    fun `fixture with every kind of entry gives the five visible messages in file order`() =
        runTest {
            ConversationFileKit.place(home, "conversation-mixed.jsonl")

            val result = history().page().ok()

            result.messages.map { Triple(it.role, it.text, it.at?.toString()) } shouldBe
                listOf(
                    Triple(ConversationRole.USER, "First question", "2026-01-01T10:00:00Z"),
                    Triple(ConversationRole.ASSISTANT, "First answer", "2026-01-01T10:02:00Z"),
                    Triple(ConversationRole.USER, "Second\nquestion", "2026-01-01T10:07:00Z"),
                    Triple(ConversationRole.ASSISTANT, "Second answer", "2026-01-01T10:08:00Z"),
                    Triple(ConversationRole.USER, "Third question", null),
                )
            result.older shouldBe null
        }

    @Test
    fun `lines that are not messages between messages do not count against the page size`() =
        runTest {
            val file = ConversationFileKit.place(home, "conversation-mixed.jsonl")
            val offsets = ConversationFileKit.lineOffsets(file)

            val newest = history().page(limit = 2).ok()
            val rest = history().page(before = newest.older, limit = 10).ok()

            newest.messages.map { it.text } shouldBe listOf("Second answer", "Third question")
            // "Second answer" is the 11th line of the fixture
            newest.older shouldBe HistoryCursor(offsets[10])
            rest.messages.map { it.text } shouldBe listOf("First question", "First answer", "Second\nquestion")
        }

    @Test
    fun `list carries the name and the last prompt of the file`() =
        runTest {
            ConversationFileKit.place(home, "custom-title.jsonl")

            val listed = (history().list(listOf(quayside), since = null) as Outcome.Ok).value.single()

            listed.name shouldBe "Release prep"
            listed.lastPrompt shouldBe "Final check"
        }

    private companion object {
        const val KILOBYTE = 1024
    }
}
