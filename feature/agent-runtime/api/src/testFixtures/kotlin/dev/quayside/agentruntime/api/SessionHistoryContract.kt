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

    private companion object {
        const val STEP = 3_600L
        const val YEAR = 31_536_000L
        const val ID_TAIL = 12
    }
}
