package dev.quayside.agentruntime.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

/** `BackwardLines`: lines from the end of a file in small chunks (AM-315, ADR 0034, decision 2 and 3). */
internal class BackwardLinesTest {
    @TempDir
    lateinit var dir: Path

    private fun file(text: String): Path =
        dir.resolve("f.jsonl").also { Files.write(it, text.toByteArray(Charsets.UTF_8)) }

    private fun read(
        text: String,
        end: Long = text.toByteArray(Charsets.UTF_8).size.toLong(),
        maxLineBytes: Int = BackwardLines.MAX_LINE_BYTES,
    ): List<FileLine> = BackwardLines.read(file(text), end, CHUNK, maxLineBytes).toList()

    @Test
    fun `file larger than the chunk yields every line newest first`() {
        val all = (1..10).map { "line-number-$it" }

        val lines = read(all.joinToString("\n", postfix = "\n"))

        lines.map { it.text } shouldBe all.reversed()
        lines.map { it.offset } shouldBe all.indices.map { i -> all.take(i).sumOf { it.length + 1L } }.reversed()
    }

    @Test
    fun `line cut by a chunk boundary is joined`() {
        val long = "x".repeat(CHUNK * 2 + 5)

        read("a\n$long\nb\n").map { it.text } shouldBe listOf("b", long, "a")
    }

    @ParameterizedTest(name = "padding {0}")
    @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16])
    fun `emoji split by a chunk boundary decodes intact`(padding: Int) {
        // the 4-byte emoji and the 2-byte letter land on every offset against the 16-byte chunk edges
        val line = "p".repeat(padding) + "😀ó😀" + "q".repeat(CHUNK - padding)

        read("first\n$line\nlast\n").map { it.text } shouldBe listOf("last", line, "first")
    }

    @Test
    fun `line longer than the line limit is skipped and the next one is read`() {
        val lines = read("a\n${"y".repeat(100)}\nb\n", maxLineBytes = 20)

        lines.map { it.text } shouldBe listOf("b", "a")
    }

    @Test
    fun `line of exactly the limit is kept and one byte more is skipped`() {
        val kept = "k".repeat(20)

        read("$kept\n${"s".repeat(21)}\n$kept\n", maxLineBytes = 20).map { it.text } shouldBe listOf(kept, kept)
    }

    @Test
    fun `limit counts bytes, not characters`() {
        // 11 characters but 22 bytes: over a limit of 20 bytes
        read("ok\n${"ó".repeat(11)}\n", maxLineBytes = 20).map { it.text } shouldBe listOf("ok")
    }

    @Test
    fun `file without a trailing newline yields the last line`() {
        read("a\nb\nlast").map { it.text } shouldBe listOf("last", "b", "a")
    }

    @Test
    fun `start offset in the middle of a line skips the partial line`() {
        val text = "aaa\nbbbb\ncc\n"

        // 6 is inside "bbbb": the partial "bb" is not a line, "aaa" is
        read(text, end = 6).map { it.text } shouldBe listOf("aaa")
        // 9 is the start of "cc": "bbbb" is complete
        read(text, end = 9).map { it.text } shouldBe listOf("bbbb", "aaa")
    }

    @Test
    fun `empty file and end at zero yield nothing`() {
        read("").shouldBe(emptyList())
        read("abc\n", end = 0) shouldBe emptyList()
    }

    private companion object {
        const val CHUNK = 16
    }
}
