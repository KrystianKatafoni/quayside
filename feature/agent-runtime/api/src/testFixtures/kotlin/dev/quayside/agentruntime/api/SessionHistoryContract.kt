package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Behaviour every [SessionHistory] must have (ENG-T2). Runs on [FakeSessionHistory] and on the file-based
 * implementation over session files in a temporary directory.
 *
 * Conversations are listed by [create] with a title, so a file-based implementation can write them; the paths need
 * not exist on disk.
 */
public abstract class SessionHistoryContract {
    /** A [SessionHistory] that holds exactly [conversations]. */
    protected abstract fun create(conversations: List<PastConversation>): SessionHistory

    /** A [SessionHistory] that holds [conversation] with exactly [messages] (oldest first) in its file. */
    protected abstract fun createWith(
        conversation: PastConversation,
        messages: List<ConversationMessage>,
    ): SessionHistory

    private val project: Path = Path.of("/work/p")

    private fun conv(
        n: Int,
        cwd: Path = project,
        modifiedAt: java.time.Instant = T0.plusSeconds(n * STEP),
    ): PastConversation =
        aPastConversation(
            conversationId = aConversationId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"),
            startCwd = cwd,
            startedAt = T0,
            lastActivityAt = modifiedAt,
            modifiedAt = modifiedAt,
            title = "title $n",
            lastPrompt = "title $n",
        )

    private suspend fun SessionHistory.ids(
        vararg dirs: Path,
        since: java.time.Instant? = null,
    ): List<String> =
        (
            list(
                dirs.toList(),
                since,
            ) as Outcome.Ok
        ).value.map {
            it.conversationId.value
                .takeLast(ID_TAIL)
                .trimStart('0')
        }

    @Test
    public fun `a session started in the project directory is listed with its fields`(): Unit =
        runTest {
            val one = conv(1)

            val result = create(listOf(one)).list(listOf(project), since = null)

            result shouldBe Outcome.Ok(listOf(one))
        }

    @Test
    public fun `a session started below the project directory is listed`(): Unit =
        runTest {
            create(listOf(conv(1, project.resolve("soft/api")))).ids(project) shouldBe listOf("1")
        }

    @Test
    public fun `a sibling directory with a common name prefix is not listed`(): Unit =
        runTest {
            val history = create(listOf(conv(1, Path.of("/work/p-old")), conv(2, Path.of("/work/other"))))

            history.ids(project) shouldBe emptyList()
        }

    @Test
    public fun `sessions come newest modified first`(): Unit =
        runTest {
            create(listOf(conv(2), conv(3), conv(1))).ids(project) shouldBe listOf("3", "2", "1")
        }

    @Test
    public fun `a session matching two directories is listed once`(): Unit =
        runTest {
            create(listOf(conv(1, project.resolve("soft")))).ids(Path.of("/work"), project) shouldBe listOf("1")
        }

    @Test
    public fun `since leaves out older files and keeps one exactly at since`(): Unit =
        runTest {
            val history = create(listOf(conv(1), conv(2), conv(3)))

            history.ids(project, since = T0.plusSeconds(2 * STEP)) shouldBe listOf("3", "2")
        }

    @Test
    public fun `no since lists any age`(): Unit =
        runTest {
            create(listOf(conv(1, modifiedAt = T0.minusSeconds(YEAR)))).ids(project) shouldBe listOf("1")
        }

    @Test
    public fun `no directories list nothing`(): Unit =
        runTest {
            create(listOf(conv(1))).list(emptyList(), since = null) shouldBe Outcome.Ok(emptyList())
        }

    @Test
    public fun `no sessions at all is ok and empty`(): Unit =
        runTest {
            create(emptyList()).list(listOf(project), since = null) shouldBe Outcome.Ok(emptyList())
        }

    private val subject: PastConversation = conv(1)

    private fun messages(count: Int): List<ConversationMessage> = (1..count).map { aConversationMessage(it) }

    private suspend fun SessionHistory.page(
        before: HistoryCursor? = null,
        limit: Int = PAGE,
        id: ConversationId = subject.conversationId,
    ): Outcome<ConversationPage, ConversationError> = conversation(id, subject.startCwd, before, limit)

    private fun Outcome<ConversationPage, ConversationError>.ok(): ConversationPage = (this as Outcome.Ok).value

    private fun List<ConversationMessage>.numbers(): List<Int> = map { it.text.removePrefix("message ").toInt() }

    @Test
    public fun `a conversation page holds the newest messages oldest first with a cursor for more`(): Unit =
        runTest {
            val page = createWith(subject, messages(30)).page().ok()

            page.messages.numbers() shouldBe (11..30).toList()
            (page.older != null) shouldBe true
        }

    @Test
    public fun `older pages continue without overlap or gap and the last one has no cursor`(): Unit =
        runTest {
            val history = createWith(subject, messages(45))

            val first = history.page().ok()
            val second = history.page(first.older).ok()
            val third = history.page(second.older).ok()

            first.messages.numbers() shouldBe (26..45).toList()
            second.messages.numbers() shouldBe (6..25).toList()
            third.messages.numbers() shouldBe (1..5).toList()
            third.older shouldBe null
        }

    @Test
    public fun `a short conversation is one page without a cursor`(): Unit =
        runTest {
            val page = createWith(subject, messages(3)).page().ok()

            page.messages.numbers() shouldBe listOf(1, 2, 3)
            page.older shouldBe null
        }

    @Test
    public fun `a cursor at the start of the file gives an empty final page`(): Unit =
        runTest {
            createWith(subject, messages(3)).page(HistoryCursor(0)) shouldBe
                Outcome.Ok(ConversationPage(emptyList(), null))
        }

    @Test
    public fun `role, text and time of a message are kept`(): Unit =
        runTest {
            val sent =
                listOf(
                    aConversationMessage(1, ConversationRole.USER, "How are you?", T0),
                    aConversationMessage(2, ConversationRole.ASSISTANT, "Fine\nand you?", T0.plusSeconds(5)),
                )

            createWith(subject, sent).page().ok().messages shouldBe sent
        }

    @Test
    public fun `limit is coerced to 1 up to 100`(): Unit =
        runTest {
            val history = createWith(subject, messages(150))

            history
                .page(limit = 0)
                .ok()
                .messages
                .numbers() shouldBe listOf(150)
            history
                .page(limit = -5)
                .ok()
                .messages
                .numbers() shouldBe listOf(150)
            history
                .page(limit = 1_000)
                .ok()
                .messages
                .numbers() shouldBe (51..150).toList()
        }

    @Test
    public fun `an unknown conversation or an id that is not a lowercase UUID is NOT_FOUND`(): Unit =
        runTest {
            // an id with hex letters, so that upper case differs from the real one
            val lettered = aPastConversation(startCwd = project, startedAt = T0, modifiedAt = T0)
            val history = createWith(lettered, messages(3))
            val upper = ConversationId(lettered.conversationId.value.uppercase())

            (history.page(id = lettered.conversationId) is Outcome.Ok) shouldBe true
            history.page(id = aConversationId("00000000-0000-4000-8000-0000000000ff")) shouldBe
                Outcome.Err(ConversationError.NOT_FOUND)
            history.page(id = upper) shouldBe Outcome.Err(ConversationError.NOT_FOUND)
            history.page(id = ConversationId("../x")) shouldBe Outcome.Err(ConversationError.NOT_FOUND)
            // a character no path can hold must not throw before the id is checked
            history.page(id = ConversationId("a\u0000b")) shouldBe Outcome.Err(ConversationError.NOT_FOUND)
        }

    @Test
    public fun `a start directory other than the one of the conversation is NOT_FOUND`(): Unit =
        runTest {
            val history = createWith(subject, messages(3))

            (history.conversation(subject.conversationId, subject.startCwd, null, PAGE) is Outcome.Ok) shouldBe true
            history.conversation(subject.conversationId, Path.of("/work/other"), null, PAGE) shouldBe
                Outcome.Err(ConversationError.NOT_FOUND)
        }

    @Test
    public fun `a cursor past the end is UNREADABLE`(): Unit =
        runTest {
            createWith(subject, messages(3)).page(HistoryCursor(1_000_000)) shouldBe
                Outcome.Err(ConversationError.UNREADABLE)
        }

    private companion object {
        const val PAGE = 20
        const val STEP = 3_600L
        const val YEAR = 31_536_000L
        const val ID_TAIL = 12
    }
}
