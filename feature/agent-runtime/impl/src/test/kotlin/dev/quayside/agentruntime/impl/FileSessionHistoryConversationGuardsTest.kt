package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aConversationMessage
import dev.quayside.agentruntime.api.aPastConversation
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
import kotlin.io.path.createDirectories

/**
 * Guards of `FileSessionHistory.conversation` (AM-315, ADR 0034, decisions 3 to 6): ids and paths, cursors, the cut of
 * long text, a read that never writes, and the log. Temporary directory only.
 */
class FileSessionHistoryConversationGuardsTest {
    @TempDir
    lateinit var home: Path

    private val logger = RecordingLogger()
    private val lowercase = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"
    private val conv = aPastConversation(aConversationId(lowercase), startCwd = quayside, modifiedAt = T0)

    private fun TestScope.history(): SessionHistory =
        sessionHistoryModule(home, StandardTestDispatcher(testScheduler), logger)

    private val notFound = Outcome.Err(ConversationError.NOT_FOUND)

    @Test
    fun `id with a path separator is NOT_FOUND without reading the target`() =
        runTest {
            // Given a perfectly good session file one directory up from where "../<id>" would point
            val dirName = SessionFiles.dirNameOf(quayside)
            writeRaw(home, quayside, SESSION_ID, conversationText(SESSION_ID, "/p", messages(2)), T0)
            val outside = home.resolve("projects/$SESSION_ID.jsonl")
            Files.copy(projectDirOf(home, quayside).resolve("$SESSION_ID.jsonl"), outside)

            // Then neither the traversal nor a separator nor an absolute path reaches it
            history().page(id = "../$SESSION_ID") shouldBe notFound
            history().page(id = "$dirName/../../projects/$SESSION_ID") shouldBe notFound
            history().page(id = outside.toString()) shouldBe notFound
            history().page(id = "..") shouldBe notFound
            // a NUL makes Path.resolve throw, so the id has to be checked before any path is built
            history().page(id = "a\u0000b") shouldBe notFound
        }

    @Test
    fun `uppercase id is NOT_FOUND although the lowercase file exists`() =
        runTest {
            writeConversationFile(home, conv, messages(2))

            (history().page(id = lowercase) is Outcome.Ok) shouldBe true
            history().page(id = lowercase.uppercase()) shouldBe notFound
        }

    @Test
    fun `missing file and a directory in its place are NOT_FOUND`() =
        runTest {
            history().page(id = lowercase) shouldBe notFound

            projectDirOf(home, quayside).resolve("$lowercase.jsonl").createDirectories()

            history().page(id = lowercase) shouldBe notFound
        }

    @Test
    fun `cursor past the end or below zero is UNREADABLE`() =
        runTest {
            val file = writeConversationFile(home, conv, messages(2))
            val size = Files.size(file)

            history().page(id = lowercase, before = HistoryCursor(size + 1)) shouldBe
                Outcome.Err(ConversationError.UNREADABLE)
            history().page(id = lowercase, before = HistoryCursor(-1)) shouldBe
                Outcome.Err(ConversationError.UNREADABLE)
            // the end itself is fine: it is where reading starts without a cursor
            history().page(id = lowercase, before = HistoryCursor(size)).ok().messages shouldBe messages(2)
        }

    @Test
    fun `text over 4000 code points is truncated and marked, 4000 is whole`() =
        runTest {
            // emoji: 4000 of them are 8000 UTF-16 units, so counting units would cut them
            val emoji = "😀"
            val exact = aConversationMessage(1, text = emoji.repeat(4_000))
            val over = aConversationMessage(2, text = emoji.repeat(4_001))
            writeConversationFile(home, conv, listOf(exact, over))

            val page = history().page(id = lowercase).ok().messages

            page[0] shouldBe exact
            page[1] shouldBe over.copy(text = emoji.repeat(4_000), truncated = true)
        }

    @Test
    fun `conversation never changes the file`() =
        runTest {
            writeConversationFile(home, conv, (1..300).map { aConversationMessage(it, text = "w".repeat(1_000)) })
            val before = fingerprint(home)

            val first = history().page(id = lowercase).ok()
            history().page(id = lowercase, before = first.older).ok()
            history().page(id = lowercase, before = HistoryCursor(1_000_000_000))

            fingerprint(home) shouldBe before
        }

    @Test
    fun `reading logs counts only, never text, a path or an id`() =
        runTest {
            val secret = "very-secret-words"
            writeConversationFile(home, conv, listOf(aConversationMessage(1, text = secret)))

            history().page(id = lowercase).ok()
            history().page(id = lowercase, before = HistoryCursor(1_000_000))
            history().page(id = "00000000-0000-4000-8000-0000000000aa")

            logger.entries.isNotEmpty() shouldBe true
            logger.entries.any { entry -> entry.fields.values.any { it is Int } } shouldBe true
            logger.entries.mention(secret) shouldBe false
            logger.entries.mention(home) shouldBe false
            logger.entries.mention(lowercase) shouldBe false
            logger.entries.mention("0000000000aa") shouldBe false
        }
}
