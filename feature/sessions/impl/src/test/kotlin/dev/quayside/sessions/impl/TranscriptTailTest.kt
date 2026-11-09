package dev.quayside.sessions.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Model and permission mode of a transcript tail (FR-S8, AM-818): pure parsing and the file read. */
class TranscriptTailTest {
    @TempDir
    lateinit var tmp: Path

    private fun assistant(model: String) = """{"type":"assistant","message":{"role":"assistant","model":"$model"}}"""

    private fun user(mode: String) = """{"type":"user","permissionMode":"$mode","message":{"role":"user"}}"""

    @Test
    fun `the newest model and the newest mode win`() {
        val text =
            listOf(
                assistant("claude-old"),
                user("default"),
                assistant("claude-new"),
                user("plan"),
            ).joinToString("\n")

        TranscriptTail.parse(text) shouldBe TranscriptTail("claude-new", "plan")
    }

    @Test
    fun `a value found only in an older entry is still used`() {
        val text = listOf(user("acceptEdits"), assistant("claude-1"), """{"type":"summary"}""").joinToString("\n")

        TranscriptTail.parse(text) shouldBe TranscriptTail("claude-1", "acceptEdits")
    }

    @Test
    fun `synthetic models, lines that are no objects and unsafe values are skipped`() {
        val text =
            listOf(
                assistant("claude-real"),
                assistant("<synthetic>"),
                "not json at all",
                "[1,2]",
                "",
                """{"permissionMode":"bad value with spaces"}""",
                """{"permissionMode":42}""",
                """{"message":{"model":"${"x".repeat(n = 81)}"}}""",
                user("default"),
            ).joinToString("\n")

        TranscriptTail.parse(text) shouldBe TranscriptTail("claude-real", "default")
    }

    @Test
    fun `nothing found gives nulls`() {
        TranscriptTail.parse("") shouldBe TranscriptTail(null, null)
        TranscriptTail.parse("""{"type":"user","message":{"role":"user"}}""") shouldBe TranscriptTail(null, null)
    }

    @Test
    fun `the model must be a string inside message`() {
        TranscriptTail.parse("""{"model":"top-level"}""") shouldBe TranscriptTail(null, null)
        TranscriptTail.parse("""{"message":{"model":7}}""") shouldBe TranscriptTail(null, null)
        TranscriptTail.parse("""{"message":"text"}""") shouldBe TranscriptTail(null, null)
    }

    @Test
    fun `a missing file or a directory is unknown`() {
        TranscriptTail.read(tmp.resolve("missing.jsonl")) shouldBe null
        TranscriptTail.read(tmp) shouldBe null
    }

    @Test
    fun `a file is read from its end`() {
        val file = tmp.resolve("t.jsonl")
        Files.writeString(file, listOf(assistant("claude-a"), user("plan")).joinToString("\n") + "\n")

        TranscriptTail.read(file) shouldBe TranscriptTail("claude-a", "plan")
    }

    @Test
    fun `only the tail of a large file is read`() {
        val file = tmp.resolve("big.jsonl")
        val filler = "x".repeat(n = 1000)
        val old = assistant("claude-too-old") + "\n"
        val body = (1..(TranscriptTail.TAIL_BYTES / 1000).toInt() + 50).joinToString("\n") { """{"pad":"$filler"}""" }
        Files.writeString(file, old + body + "\n" + user("plan") + "\n")

        // The model sits before the tail, the mode inside it; a cut first line of the tail is ignored.
        TranscriptTail.read(file) shouldBe TranscriptTail(null, "plan")
    }
}
