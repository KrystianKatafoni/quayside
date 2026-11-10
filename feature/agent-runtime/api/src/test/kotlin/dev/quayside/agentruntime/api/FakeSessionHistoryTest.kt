package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The scripting of [FakeSessionHistory] that other modules' tests rely on. */
class FakeSessionHistoryTest {
    private val dirs = listOf(A_PROJECT_DIR)

    @Test
    fun `calls are recorded with their arguments, also the failed ones`() =
        runTest {
            val fake = FakeSessionHistory()
            fake.failNext(HistoryError.UNREADABLE)

            fake.list(dirs, since = null) shouldBe Outcome.Err(HistoryError.UNREADABLE)
            fake.list(dirs, since = aPastConversation().modifiedAt)

            fake.calls shouldBe listOf(HistoryCall(dirs, null), HistoryCall(dirs, aPastConversation().modifiedAt))
        }

    @Test
    fun `failNext fails once and failing fails until cleared`() =
        runTest {
            val fake = FakeSessionHistory(listOf(aPastConversation()))
            fake.failNext(HistoryError.UNREADABLE)
            fake.list(dirs, null) shouldBe Outcome.Err(HistoryError.UNREADABLE)
            (fake.list(dirs, null) is Outcome.Ok) shouldBe true

            fake.failing(HistoryError.UNREADABLE)
            fake.list(dirs, null) shouldBe Outcome.Err(HistoryError.UNREADABLE)
            fake.list(dirs, null) shouldBe Outcome.Err(HistoryError.UNREADABLE)
            fake.failing(null)
            (fake.list(dirs, null) is Outcome.Ok) shouldBe true
        }

    @Test
    fun `put replaces a conversation with the same id and delete removes it`() =
        runTest {
            val fake = FakeSessionHistory(listOf(aPastConversation(title = "old")))

            fake.put(aPastConversation(title = "new"))
            fake.conversations.map { it.title } shouldBe listOf("new")

            fake.delete(aPastConversation().conversationId)
            fake.conversations shouldBe emptyList()
            fake.list(listOf(Path.of("/")), null) shouldBe Outcome.Ok(emptyList())
        }
}
