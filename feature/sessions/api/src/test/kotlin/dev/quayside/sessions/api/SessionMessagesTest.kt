package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The message types of the history of a disk session and the scripting of [FakeSessions] that other tests use. */
class SessionMessagesTest {
    @Test
    fun `toString of a message hides its text`() {
        aSessionMessage(text = "secret words").toString() shouldNotContain "secret"
    }

    @Test
    fun `every read is recorded with its cursor, also a failed one`() =
        runTest {
            val id = SessionId("00000000-0000-4000-8000-000000000001")
            val fake =
                FakeSessions(
                    SessionsWorld(projects(P1 to P1_DIR), pastSessions = listOf(WorldPastSession(id, P1_DIR))),
                )
            fake.conversationFiles.failNext()

            fake.conversation(id, null)
            val second = fake.conversation(id, MessageCursor(0))

            fake.conversationFiles.reads shouldBe listOf(id to null, id to MessageCursor(0))
            second shouldBe Outcome.Ok(SessionMessagePage(emptyList(), null))
        }
}
