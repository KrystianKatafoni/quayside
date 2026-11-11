package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationPage
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.agentruntime.api.aConversationMessage
import dev.quayside.core.domain.Outcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/** Helpers of the tests of `FileSessionHistory.conversation` over files in a temporary Claude home. */
internal object ConversationFileKit {
    val quayside: Path = Path.of("/Users/test/projects/quayside")

    /** Copies the synthetic fixture [name] to where Claude Code keeps the file of [SESSION_ID] started in [cwd]. */
    fun place(
        home: Path,
        name: String,
        cwd: Path = quayside,
    ): Path {
        val text =
            checkNotNull(ConversationFileKit::class.java.getResource("/fixtures/session-files/synthetic/$name")) {
                "fixture $name missing"
            }.readText()
        val file = projectDirOf(home, cwd).createDirectories().resolve("$SESSION_ID.jsonl")
        Files.writeString(file, text)
        return file
    }

    /** Start of every line of [file] in bytes (the cursor of the message on that line). */
    fun lineOffsets(file: Path): List<Long> {
        val bytes = Files.readAllBytes(file)
        return listOf(0L) +
            bytes.indices
                .filter { bytes[it] == '\n'.code.toByte() }
                .map { it + 1L }
                .dropLast(1)
    }

    fun messages(count: Int): List<ConversationMessage> = (1..count).map { aConversationMessage(it) }

    fun Outcome<ConversationPage, ConversationError>.ok(): ConversationPage = (this as Outcome.Ok).value

    suspend fun SessionHistory.page(
        id: String = SESSION_ID,
        cwd: Path = quayside,
        before: HistoryCursor? = null,
        limit: Int = PAGE,
    ): Outcome<ConversationPage, ConversationError> = conversation(ConversationId(id), cwd, before, limit)

    /** Every page from the end back to the start of the file, oldest message first. */
    suspend fun SessionHistory.everything(limit: Int = PAGE): List<ConversationMessage> {
        var cursor: HistoryCursor? = null
        val pages = mutableListOf<List<ConversationMessage>>()
        do {
            val page = page(before = cursor, limit = limit).ok()
            pages.add(0, page.messages)
            cursor = page.older
        } while (cursor != null)
        return pages.flatten()
    }

    const val PAGE = 20
}
