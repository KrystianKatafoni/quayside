package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.A_CONVERSATION_UUID
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aPastConversation
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * [sessionHistoryModule] on files in a temporary directory (ENG-T3, docs/12 §4): what is opened, what is cached and
 * what is logged. The shared behaviour is in [FileSessionHistoryContractTest]. No real `~/.claude`.
 */
class FileSessionHistoryTest {
    @TempDir
    lateinit var home: Path

    private val project: Path = Path.of("/work/p")
    private val logger = RecordingLogger()

    private fun TestScope.history() = sessionHistoryModule(home, StandardTestDispatcher(testScheduler), logger)

    private fun conv(
        n: Int,
        cwd: Path = project,
        title: String? = "title $n",
        modifiedAt: Instant = T0.plusSeconds(n * STEP),
    ): PastConversation =
        aPastConversation(
            conversationId = aConversationId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"),
            startCwd = cwd,
            startedAt = T0,
            lastActivityAt = modifiedAt,
            modifiedAt = modifiedAt,
            title = title,
        )

    private suspend fun SessionHistory.titles(since: Instant? = null): List<String?> =
        ((list(listOf(project), since) as Outcome.Ok).value).map { it.title }

    @Test
    fun `a file older than since is never opened`() =
        runTest {
            // Given an old file that is not a session and a newer one that is not either
            writeRaw(home, project, GARBAGE_OLD, "not json at all", T0)
            writeRaw(home, project, GARBAGE_NEW, "not json either", T0.plusSeconds(STEP))

            // When listing from a moment between them
            history().list(listOf(project), since = T0.plusSeconds(1))

            // Then only the newer one was read (a read file that is not a session is counted as skipped)
            logger.entries.countOf("NOT_JSONL") shouldBe 1
        }

    @Test
    fun `a file exactly at since is listed`() =
        runTest {
            writeSessionFile(home, conv(1))

            history().titles(since = T0.plusSeconds(STEP)) shouldBe listOf("title 1")
        }

    @Test
    fun `a session started in a sibling with a common prefix is not listed`() =
        runTest {
            // /work/p-old is encoded as "-work-p-old", which starts with "-work-p-": a candidate directory of /work/p
            writeSessionFile(home, conv(1, cwd = Path.of("/work/p-old")))
            writeSessionFile(home, conv(2))

            history().titles() shouldBe listOf("title 2")
        }

    @Test
    fun `a file in a nested directory of projects is skipped`() =
        runTest {
            val file = writeSessionFile(home, conv(1))
            val nested = file.parent.resolve("sub").createDirectories()
            Files.copy(file, nested.resolve(file.fileName))

            history().titles() shouldBe listOf("title 1")
        }

    @Test
    fun `a file that is not a session is skipped and does not fail the listing`() =
        runTest {
            writeRaw(home, project, GARBAGE_NEW, "not json", T0)
            writeSessionFile(home, conv(1))

            history().titles() shouldBe listOf("title 1")
        }

    @Test
    fun `an unchanged file is read once across two calls`() =
        runTest {
            // Given a listed file whose content is then replaced by one of the same size, with the same mtime
            val file = writeSessionFile(home, conv(1, title = "aaaa"))
            val history = history()
            history.titles() shouldBe listOf("aaaa")
            val mtime = Files.getLastModifiedTime(file)
            file.writeText(Files.readString(file).replace("aaaa", "bbbb"))
            Files.setLastModifiedTime(file, mtime)

            // Then the second call still has the first reading: the cache key (path, size, mtime) did not change
            history.titles() shouldBe listOf("aaaa")
        }

    @Test
    fun `a changed mtime reads the file again`() =
        runTest {
            val file = writeSessionFile(home, conv(1, title = "aaaa"))
            val history = history()
            history.titles() shouldBe listOf("aaaa")
            file.writeText(Files.readString(file).replace("aaaa", "bbbb"))
            Files.setLastModifiedTime(file, FileTime.from(T0.plusSeconds(2 * STEP)))

            history.titles() shouldBe listOf("bbbb")
        }

    @Test
    fun `a changed size reads the file again`() =
        runTest {
            val file = writeSessionFile(home, conv(1, title = "aaaa"))
            val history = history()
            history.titles() shouldBe listOf("aaaa")
            val mtime = Files.getLastModifiedTime(file)
            file.writeText(Files.readString(file).replace("aaaa", "bbbbbb"))
            Files.setLastModifiedTime(file, mtime)

            history.titles() shouldBe listOf("bbbbbb")
        }

    @Test
    fun `a file that could not be read is not remembered and is read again once it can`() =
        runTest {
            // Given a session file without read permission (the listing sees it, the read fails)
            val posix = Files.getFileStore(home).supportsFileAttributeView("posix")
            assumeTrue(posix && System.getProperty("user.name") != "root", "needs POSIX and a non-root user")
            val file = writeSessionFile(home, conv(1))
            val history = history()
            Files.setPosixFilePermissions(file, emptySet())
            history.titles() shouldBe emptyList()

            // When the permission comes back, with the same size and mtime
            val mtime = Files.getLastModifiedTime(file)
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
            Files.setLastModifiedTime(file, mtime)

            // Then the next listing reads the file again
            history.titles() shouldBe listOf("title 1")
        }

    @Test
    fun `a deleted file disappears from the next listing`() =
        runTest {
            val file = writeSessionFile(home, conv(1))
            val history = history()
            history.titles() shouldBe listOf("title 1")

            Files.delete(file)

            history.titles() shouldBe emptyList()
        }

    @Test
    fun `a missing projects directory is ok and empty`() =
        runTest {
            history().list(listOf(project), since = null) shouldBe Outcome.Ok(emptyList())
        }

    @Test
    fun `an unreadable projects directory is UNREADABLE`() =
        runTest {
            // projects is a file, not a directory: listing it fails the way a directory without access does
            home.resolve("projects").writeText("not a directory")

            history().list(listOf(project), since = null) shouldBe Outcome.Err(HistoryError.UNREADABLE)
        }

    @Test
    fun `listing never changes files`() =
        runTest {
            writeSessionFile(home, conv(1))
            writeSessionFile(home, conv(2, cwd = Path.of("/work/other")))
            writeRaw(home, project, GARBAGE_NEW, "not json", T0)
            val before = fingerprint(home)

            val history = history()
            history.list(listOf(project), since = null)
            history.list(listOf(project, Path.of("/work")), since = T0)

            fingerprint(home) shouldBe before
        }

    @Test
    fun `skipped files are logged as counts without paths or ids`() =
        runTest {
            writeRaw(home, project, GARBAGE_NEW, "not json", T0)
            writeRaw(home, project, A_CONVERSATION_UUID, "also not json", T0)
            writeSessionFile(home, conv(1, title = "secret plan"))

            history().list(listOf(project), since = null)

            logger.entries.countOf("NOT_JSONL") shouldBe 2
            logger.entries.mention(home) shouldBe false
            logger.entries.mention(GARBAGE_NEW) shouldBe false
            logger.entries.mention(A_CONVERSATION_UUID) shouldBe false
            logger.entries.mention("secret plan") shouldBe false
            logger.entries.mention("/work/p") shouldBe false
        }

    private companion object {
        const val STEP = 3_600L
        const val GARBAGE_OLD = "11111111-1111-4111-8111-111111111111"
        const val GARBAGE_NEW = "22222222-2222-4222-8222-222222222222"
    }
}
