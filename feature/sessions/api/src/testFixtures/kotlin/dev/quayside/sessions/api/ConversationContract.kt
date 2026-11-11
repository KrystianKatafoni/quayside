package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * What every sessions system that lists session files must do for the history of a disk session and for its name and
 * summary (FR-P9, AM-315, ADR 0034, ENG-T2). Runs on [FakeSessions] and on `sessionsModule` over a
 * `FakeSessionHistory`, so the fake used in window tests cannot drift from the implementation.
 */
public abstract class ConversationContract {
    /** A harness whose supervisor, projects and disk look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): ResumeHarness

    private val id: SessionId = SessionId("00000000-0000-4000-8000-000000000001")
    private val supervisorId: SessionId = SessionId("7c5dcf5d")
    private val unknownId: SessionId = SessionId("00000000-0000-4000-8000-0000000000aa")
    private val unavailable = Outcome.Err(SessionError.UnknownSession(id))

    private fun file(
        title: String? = "Fix the login bug",
        name: String? = null,
        lastPrompt: String? = null,
        cwd: Path = P1_DIR,
    ) = WorldPastSession(id, cwd, T0.minusSeconds(HOUR), title, name = name, lastPrompt = lastPrompt)

    private suspend fun TestScope.settled(
        vararg files: WorldPastSession,
        messages: List<SessionMessage>? = null,
    ): ResumeHarness {
        val supervised = listOf(sessionIn(supervisorId.value, P1_DIR))
        val world = SessionsWorld(projects(P1 to P1_DIR), supervised, pastSessions = files.toList())
        return create(world).also { harness ->
            messages?.let { harness.givenConversation(id, it) }
            harness.settle()
        }
    }

    private fun conversations(count: Int): List<SessionMessage> = (1..count).map { aSessionMessage(it) }

    private fun Outcome<SessionMessagePage, SessionError>.page(): SessionMessagePage = (this as Outcome.Ok).value

    private fun List<SessionMessage>.numbers(): List<Int> = map { it.text.removePrefix("message ").toInt() }

    @Test
    public fun `a disk session gives its newest 20 messages oldest first with a cursor for more`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(25))

            val page = harness.monitor.conversation(id, before = null).page()

            page.messages.numbers() shouldBe (6..25).toList()
            (page.older != null) shouldBe true
        }

    @Test
    public fun `the cursor of a page leads to the earlier messages and the last page has none`(): Unit =
        runTest {
            val all = conversations(25)
            val harness = settled(file(), messages = all)
            val first = harness.monitor.conversation(id, null).page()

            val second = harness.monitor.conversation(id, first.older).page()

            second.messages shouldBe all.take(5)
            second.older shouldBe null
            second.messages + first.messages shouldBe all
        }

    @Test
    public fun `role, text and time of a message are kept`(): Unit =
        runTest {
            val sent =
                listOf(
                    aSessionMessage(1, MessageRole.USER, "Why?", T0),
                    aSessionMessage(2, MessageRole.ASSISTANT, "Because.\nReally.", null, truncated = true),
                )
            val harness = settled(file(), messages = sent)

            harness.monitor.conversation(id, null) shouldBe Outcome.Ok(SessionMessagePage(sent, null))
        }

    @Test
    public fun `a session file without messages gives an empty page`(): Unit =
        runTest {
            val harness = settled(file())

            harness.monitor.conversation(id, null) shouldBe Outcome.Ok(SessionMessagePage(emptyList(), null))
        }

    @Test
    public fun `a supervisor session and an unknown id are UnknownSession`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(3))

            val supervisor = harness.monitor.conversation(supervisorId, null)
            val unknown = harness.monitor.conversation(unknownId, null)

            supervisor shouldBe Outcome.Err(SessionError.UnknownSession(supervisorId))
            unknown shouldBe Outcome.Err(SessionError.UnknownSession(unknownId))
        }

    @Test
    public fun `a hidden session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(3))
            harness.commands.remove(id)
            harness.settle()

            harness.monitor.conversation(id, null) shouldBe unavailable
        }

    @Test
    public fun `a session whose file is gone is UnknownSession`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(3))

            harness.removeSessionFile(id)

            harness.monitor.conversation(id, null) shouldBe unavailable
        }

    @Test
    public fun `a read that fails is COMMAND_FAILED once and works again after`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(3))
            harness.failNextConversationRead()

            harness.monitor.conversation(id, null) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            harness.monitor
                .conversation(id, null)
                .page()
                .messages
                .numbers() shouldBe listOf(1, 2, 3)
        }

    @Test
    public fun `a cursor outside the file is COMMAND_FAILED`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(3))

            harness.monitor.conversation(id, MessageCursor(1_000_000)) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
        }

    @Test
    public fun `reading the history does not change the snapshot`(): Unit =
        runTest {
            val harness = settled(file(), messages = conversations(30))
            val before = harness.monitor.snapshot.value

            val first = harness.monitor.conversation(id, null).page()
            harness.monitor.conversation(id, first.older)

            harness.monitor.snapshot.value shouldBe before
        }

    @Test
    public fun `the name from the file wins over the first message, which is the summary`(): Unit =
        runTest {
            val session = settled(file(name = "Named by Claude")).onlyDisk()

            session.name shouldBe "Named by Claude"
            session.summary shouldBe "Fix the login bug"
        }

    @Test
    public fun `without a name from the file the first message names the session`(): Unit =
        runTest {
            settled(file()).onlyDisk().name shouldBe "Fix the login bug"
        }

    @Test
    public fun `the summary is first and last message when they differ and one of them when they do not`(): Unit =
        runTest {
            settled(file(title = "First", lastPrompt = "Last")).onlyDisk().summary shouldBe "First \u2192 Last"
            settled(file(title = "Same", lastPrompt = "Same")).onlyDisk().summary shouldBe "Same"
            settled(file(title = null, lastPrompt = "Only last")).onlyDisk().summary shouldBe "Only last"
            settled(file(title = null)).onlyDisk().summary shouldBe null
        }

    private fun ResumeHarness.onlyDisk(): Session =
        monitor.snapshot.value.sessions
            .single { it.origin == SessionOrigin.DISK }

    private companion object {
        const val HOUR = 3_600L
    }
}
