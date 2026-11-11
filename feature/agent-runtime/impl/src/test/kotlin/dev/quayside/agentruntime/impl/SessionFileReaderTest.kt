package dev.quayside.agentruntime.impl

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** [SessionFileReader] on real files in a temporary directory (docs/12 §4); no Claude Code, no network. */
class SessionFileReaderTest {
    @TempDir
    lateinit var dir: Path

    private fun TestScope.reader(
        headBytes: Int = DEFAULT_BYTES,
        tailBytes: Int = DEFAULT_BYTES,
    ) = SessionFileReader(StandardTestDispatcher(testScheduler), headBytes, tailBytes)

    private fun session(
        text: String,
        name: String = "$SESSION_ID.jsonl",
    ): Path = dir.resolve(name).also { it.writeText(text) }

    /** A line of about [size] bytes that has no cwd, no timestamp and no message. */
    private fun padding(size: Int): String = """{"type":"progress","pad":"${"x".repeat(size)}"}"""

    @Test
    fun `small file is read whole`() =
        runTest {
            val first = sessionEntry(timestamp = FIRST_TIME)
            val file = session(lines(first, sessionEntry(type = "assistant", timestamp = LAST_TIME)))

            val result = reader().read(file)

            summaryOf(result) shouldBe
                SessionFileSummary(SESSION_ID, Path.of("/p"), at(FIRST_TIME), at(LAST_TIME), false, "hello")
                    .copy(lastPrompt = "hello")
        }

    @Test
    fun `large file reads only head and tail`() =
        runTest {
            val middle = sessionEntry(cwd = "/middle", timestamp = SECOND_TIME, content = null)
            val half = List(MIB / 2 / PADDING_LINE) { padding(PADDING_LINE) }
            val text =
                lines(sessionEntry(timestamp = FIRST_TIME), *half.toTypedArray(), middle, *half.toTypedArray()) +
                    lines(sessionEntry(type = "assistant", timestamp = LAST_TIME))
            val file = session(text)
            (file.fileSize() > MIB) shouldBe true

            val result = summaryOf(reader().read(file))

            result.cwdChanged shouldBe false
            result.lastActivityAt shouldBe at(LAST_TIME)
            result.title shouldBe "hello"
        }

    @Test
    fun `head limit is honoured, a message beyond it gives no title`() =
        runTest {
            val late = sessionEntry(cwd = "/p", content = parts("late title"), timestamp = SECOND_TIME)
            val filler = List(FILLER_LINES) { padding(PADDING_LINE) }
            val text =
                lines(
                    sessionEntry(content = null, timestamp = FIRST_TIME),
                    *filler.toTypedArray(),
                    late,
                    *filler.toTypedArray(),
                ) +
                    lines(sessionEntry(type = "assistant", content = null, timestamp = LAST_TIME))

            val result = summaryOf(reader(headBytes = SMALL, tailBytes = SMALL).read(session(text)))

            result.title shouldBe null
            result.startedAt shouldBe at(FIRST_TIME)
            result.lastActivityAt shouldBe at(LAST_TIME)
        }

    @Test
    fun `tail limit is honoured, a cwd before the tail is not seen`() =
        runTest {
            val early = sessionEntry(type = "assistant", cwd = "/early", content = null, timestamp = SECOND_TIME)
            val filler = List(FILLER_LINES) { padding(PADDING_LINE) }
            val text =
                lines(sessionEntry(timestamp = FIRST_TIME), *filler.toTypedArray(), early, *filler.toTypedArray()) +
                    lines(sessionEntry(type = "assistant", content = null, timestamp = LAST_TIME))

            val result = summaryOf(reader(headBytes = SMALL, tailBytes = SMALL).read(session(text)))

            result.lastActivityAt shouldBe at(LAST_TIME)
            result.cwdChanged shouldBe false
        }

    @Test
    fun `reading runs on the injected dispatcher`() =
        runTest {
            val counting = CountingDispatcher(StandardTestDispatcher(testScheduler))

            SessionFileReader(counting).read(session(lines(sessionEntry())))

            counting.dispatches shouldBeGreaterThan 0
        }

    @Test
    fun `session id comes from the file name`() =
        runTest {
            val result = reader().read(session(lines(sessionEntry()), name = "${SESSION_ID.dropLast(1)}9.jsonl"))

            problemOf(result) shouldBe SessionFileProblem.ID_MISMATCH
        }

    @Test
    fun `missing file is UNREADABLE`() =
        runTest {
            val result = reader().read(dir.resolve("missing.jsonl"))

            problemOf(result) shouldBe SessionFileProblem.UNREADABLE
        }

    @Test
    fun `directory instead of file is UNREADABLE`() =
        runTest {
            val result = reader().read(dir.resolve("$SESSION_ID.jsonl").createDirectories())

            problemOf(result) shouldBe SessionFileProblem.UNREADABLE
        }

    @Test
    fun `empty file is NOT_JSONL`() =
        runTest {
            problemOf(reader().read(session(""))) shouldBe SessionFileProblem.NOT_JSONL
        }

    @Test
    fun `invalid UTF-8 on one line does not throw and does not hide the other lines`() =
        runTest {
            val file = dir.resolve("$SESSION_ID.jsonl")
            file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte(), 0xFE.toByte(), '\n'.code.toByte()))
            file.toFile().appendText(lines(sessionEntry(timestamp = FIRST_TIME)))

            summaryOf(reader().read(file)).startCwd shouldBe Path.of("/p")
        }

    @Test
    fun `file of invalid UTF-8 only does not throw`() =
        runTest {
            val file = dir.resolve("$SESSION_ID.jsonl")
            file.writeBytes(ByteArray(BINARY_BYTES) { (0x80 + it % 0x7F).toByte() })

            problemOf(reader().read(file)) shouldBe SessionFileProblem.NOT_JSONL
        }

    private class CountingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    private companion object {
        const val DEFAULT_BYTES = 64 * 1024
        const val MIB = 1024 * 1024
        const val PADDING_LINE = 1000
        const val SMALL = 512
        const val FILLER_LINES = 20
        const val BINARY_BYTES = 2048
    }
}
