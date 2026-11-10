package dev.quayside.agentruntime.impl

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Edge cases of [SessionFiles] and [SessionFileReader] added after the mutation round of AM-311. */
internal class SessionFilesEdgeCasesTest {
    @TempDir
    lateinit var dir: Path

    private fun titleOf(content: String): String? =
        summaryOf(parseWhole(lines(sessionEntry(content = JsonPrimitive(content))))).title

    @Test
    fun `digits and the edges of the letter ranges stay in the directory name`() {
        SessionFiles.dirNameOf(Path.of("/AZaz09")) shouldBe "-AZaz09"
    }

    @Test
    fun `a sidechain flag on the first entry makes the file a sidechain even when later entries are normal`() {
        val text = lines(withFlag(sessionEntry(), "isSidechain", true), sessionEntry(type = "assistant"))

        problemOf(parseWhole(text)) shouldBe SessionFileProblem.SIDECHAIN
    }

    @Test
    fun `a sidechain flag on a later entry does not hide a session started normally`() {
        val text = lines(sessionEntry(), withFlag(sessionEntry(type = "assistant"), "isSidechain", true))

        summaryOf(parseWhole(text)).startCwd shouldBe Path.of("/p")
    }

    @Test
    fun `runs of blanks and line breaks collapse into one space and the ends are trimmed`() {
        titleOf("  first \n\n second\t\t third  ") shouldBe "first second third"
    }

    @Test
    fun `a blank message is not a title`() {
        val text = lines(sessionEntry(content = JsonPrimitive("  \n ")), sessionEntry(content = JsonPrimitive("real")))

        summaryOf(parseWhole(text)).title shouldBe "real"
    }

    @Test
    fun `a part that is not of type text is not a title even when it has a text field`() {
        val image = """{"type":"image","text":"not a message"}"""
        val entry = """{"type":"user","sessionId":"$SESSION_ID","cwd":"/p","message":{"content":[$image]}}"""

        summaryOf(parseWhole(lines(entry))).title shouldBe null
    }

    @Test
    fun `candidates of a project whose name has exactly 200 characters do not take a longer sibling`() {
        val project = Path.of("/Users/test/" + "x".repeat(188))
        val own = "-Users-test-" + "x".repeat(188)

        SessionFiles.candidates(project, listOf(own, "${own}y", "$own-sub")) shouldBe listOf(own, "$own-sub")
    }

    @Test
    fun `a tail limit larger than the file is not an error`() =
        runTest {
            val filler = List(FILLER) { """{"type":"progress","pad":"${"x".repeat(LINE)}"}""" }
            val file = dir.resolve("$SESSION_ID.jsonl")
            val last = sessionEntry(timestamp = LAST_TIME)
            file.writeText(lines(sessionEntry(timestamp = FIRST_TIME), *filler.toTypedArray(), last))

            val reader = SessionFileReader(StandardTestDispatcher(testScheduler), headBytes = SMALL, tailBytes = HUGE)

            summaryOf(reader.read(file)).lastActivityAt shouldBe at(LAST_TIME)
        }

    @Test
    fun `a user message that only the tail holds gives no title`() {
        val head = lines(sessionEntry(type = "assistant", content = null, timestamp = FIRST_TIME))
        val tail = lines(sessionEntry(content = JsonPrimitive("only in the tail"), timestamp = LAST_TIME))

        summaryOf(SessionFiles.parse(SESSION_ID, head, tail, headIsWhole = false)).title shouldBe null
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "<command-name>/clear</command-name>",
            "<command-message>init</command-message>",
            "<local-command-stdout>x</local-command-stdout>",
        ],
    )
    fun `command and local command output are not titles`(command: String) {
        val text = lines(sessionEntry(content = JsonPrimitive(command)), sessionEntry(content = JsonPrimitive("real")))

        summaryOf(parseWhole(text)).title shouldBe "real"
    }

    @Test
    fun `an ordinary message that starts with an angle bracket can be a title`() {
        titleOf("<b>bold</b> idea") shouldBe "<b>bold</b> idea"
    }

    @Test
    fun `a cwd that is not a valid path is skipped`() {
        val text = lines(sessionEntry(cwd = NUL_CWD), sessionEntry(type = "assistant", cwd = "/p"))

        summaryOf(parseWhole(text)).startCwd shouldBe Path.of("/p")
    }

    @Test
    fun `a file whose only cwd is not a valid path has NO_CWD, also through the reader`() =
        runTest {
            val text = lines(sessionEntry(cwd = NUL_CWD))
            val file = dir.resolve("$SESSION_ID.jsonl")
            file.writeText(text)

            problemOf(parseWhole(text)) shouldBe SessionFileProblem.NO_CWD
            problemOf(SessionFileReader(StandardTestDispatcher(testScheduler)).read(file)) shouldBe
                SessionFileProblem.NO_CWD
        }

    @Test
    fun `a file without read permission is UNREADABLE`() =
        runTest {
            val file = dir.resolve("$SESSION_ID.jsonl")
            file.writeText(lines(sessionEntry()))
            assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews(), "no POSIX permissions")
            Files.setPosixFilePermissions(file, emptySet())
            assumeTrue(!Files.isReadable(file), "the file is readable anyway (running as root?)")

            val result = SessionFileReader(StandardTestDispatcher(testScheduler)).read(file)

            problemOf(result) shouldBe SessionFileProblem.UNREADABLE
        }

    @Test
    fun `toString of a summary does not show the title`() {
        val summary = summaryOf(parseWhole(lines(sessionEntry(content = JsonPrimitive("secret words")))))

        summary.toString().contains("secret words") shouldBe false
        summary.toString().contains(SESSION_ID) shouldBe true
    }

    private companion object {
        const val NUL_CWD = "/a\u0000b"
        const val FILLER = 5
        const val LINE = 1000
        const val SMALL = 512
        const val HUGE = 1_000_000
    }
}
