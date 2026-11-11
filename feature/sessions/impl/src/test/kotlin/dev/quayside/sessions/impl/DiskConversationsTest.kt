@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationCall
import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationRole
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.aConversationMessage
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.MessageRole
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMessage
import dev.quayside.sessions.api.SessionMessagePage
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * `SessionMonitor.conversation` of `sessionsModule` over a `FakeSessionHistory` (AM-315, ADR 0034): which port call
 * a disk session makes and how its answers map. The behaviour both implementations share is in `ConversationContract`.
 */
class DiskConversationsTest {
    private val sub: Path = P1_DIR.resolve("sub")
    private val id = SessionId(uuidOf(1))
    private val conversationId = ConversationId(uuidOf(1))

    private fun numbered(count: Int): List<ConversationMessage> = (1..count).map { aConversationMessage(it) }

    @Test
    fun `disk session reads 20 messages from its start directory`() =
        runTest {
            val world = pastWorld(listOf(conversation(1, cwd = sub)))
            world.history.putMessages(conversationId, numbered(25))

            val page = world.api.monitor.conversation(id, before = null)

            (page as Outcome.Ok).value.messages.map { it.text } shouldBe (6..25).map { "message $it" }
            world.history.conversationCalls shouldBe listOf(ConversationCall(conversationId, sub, null, 20))
        }

    @Test
    fun `cursor is passed through unchanged and the older cursor comes back`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            world.history.putMessages(conversationId, numbered(30))

            val first = (world.api.monitor.conversation(id, null) as Outcome.Ok).value
            val second = world.api.monitor.conversation(id, first.older)

            world.history.conversationCalls.map { it.before } shouldBe listOf(null, HistoryCursor(10))
            first.older shouldBe MessageCursor(10)
            second shouldBe Outcome.Ok(SessionMessagePage(numbered(30).take(10).map(::mirror), older = null))
        }

    @Test
    fun `role, text, time and truncation of a message are passed on`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            val sent =
                listOf(
                    aConversationMessage(1, ConversationRole.USER, "Ask", at = null, truncated = false),
                    aConversationMessage(2, ConversationRole.ASSISTANT, "Answer", truncated = true),
                )
            world.history.putMessages(conversationId, sent)

            val page = (world.api.monitor.conversation(id, null) as Outcome.Ok).value

            page.messages shouldBe
                listOf(
                    SessionMessage(MessageRole.USER, "Ask", null, false),
                    SessionMessage(MessageRole.ASSISTANT, "Answer", sent[1].at, true),
                )
        }

    @Test
    fun `supervisor session is UnknownSession without reading`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            world.runtime.hasSession(aRuntimeSession(id = SessionId("7c5dcf5d"), conversationId = null, cwd = P1_DIR))
            advance(3.seconds)
            val supervisor = SessionId("7c5dcf5d")

            world.api.monitor.conversation(supervisor, null) shouldBe
                Outcome.Err(SessionError.UnknownSession(supervisor))

            world.history.conversationCalls.shouldBeEmpty()
        }

    @Test
    fun `unknown and hidden sessions are UnknownSession without reading`() =
        runTest {
            val world = pastWorld(listOf(conversation(1), conversation(2)))
            world.api.commands.remove(SessionId(uuidOf(2)))
            runCurrent()
            val unknown = SessionId(uuidOf(9))

            world.api.monitor.conversation(unknown, null) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))
            world.api.monitor.conversation(SessionId(uuidOf(2)), null) shouldBe
                Outcome.Err(SessionError.UnknownSession(SessionId(uuidOf(2))))

            world.history.conversationCalls.shouldBeEmpty()
        }

    @Test
    fun `NOT_FOUND maps to UnknownSession`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            // the file is gone but the next scan has not run yet: the port answers NOT_FOUND
            world.history.delete(conversationId)

            world.api.monitor.conversation(id, null) shouldBe Outcome.Err(SessionError.UnknownSession(id))
            world.history.conversationCalls.size shouldBe 1
        }

    @Test
    fun `UNREADABLE maps to Runtime COMMAND_FAILED`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            world.history.failNextConversation(ConversationError.UNREADABLE)

            world.api.monitor.conversation(id, null) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
        }

    @Test
    fun `reading the conversation logs no message text`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            world.history.putMessages(conversationId, listOf(aConversationMessage(1, text = "very-secret-words")))
            world.history.failNextConversation(ConversationError.UNREADABLE)

            world.api.monitor.conversation(id, null)
            world.api.monitor.conversation(id, null)

            world.logger.entries.none { entry ->
                (listOf(entry.message) + entry.fields.values.map { it.toString() }).any { "secret" in it }
            } shouldBe true
        }

    private fun mirror(message: ConversationMessage): SessionMessage =
        SessionMessage(
            if (message.role == ConversationRole.USER) MessageRole.USER else MessageRole.ASSISTANT,
            message.text,
            message.at,
            message.truncated,
        )
}
