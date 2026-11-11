package dev.quayside.agentruntime.impl

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/** One complete line of a file: [offset] is the byte position of its first byte, [text] has no line break. */
internal data class FileLine(
    val offset: Long,
    val text: String,
)

/** Reads the lines of a file from the end, in chunks (ADR 0034). */
internal object BackwardLines {
    const val CHUNK_BYTES: Int = 64 * 1024
    const val MAX_LINE_BYTES: Int = 1024 * 1024

    /**
     * The complete lines of [file] that end at or before byte [end], newest first, read in chunks of [chunkBytes].
     * A line is cut at the `\n` byte and decoded as UTF-8 on its own; a line longer than [maxLineBytes] is skipped
     * without being held in memory; the last line of the file needs no `\n`; when [end] is inside the file and the
     * byte before it is not `\n`, the partial line that ends there is skipped. Lazy; throws `IOException`.
     * Without a byte budget, so production code (`ConversationFile`) uses [BackwardLineReader] directly; this is the
     * sequence view of the same reader that keeps the line rules testable without a budget.
     */
    fun read(
        file: Path,
        end: Long,
        chunkBytes: Int = CHUNK_BYTES,
        maxLineBytes: Int = MAX_LINE_BYTES,
    ): Sequence<FileLine> = generateSequence(BackwardLineReader(file, end, chunkBytes, maxLineBytes)::next)
}

/**
 * Pull-style reader behind [BackwardLines.read]. Opens the file only for the length of one chunk, so a consumer that
 * stops early leaves nothing open. After [budgetBytes] bytes (counted back from `end`) it stops at a line boundary,
 * or, inside a line that is skipped anyway (over the line limit), at the start of the chunk it has reached, so such a
 * line is not read to its end. [boundary] is then where a later read can go on; it skips the partial line it ends in.
 */
internal class BackwardLineReader(
    private val file: Path,
    end: Long,
    private val chunkBytes: Int,
    private val maxLineBytes: Int,
    private val budgetBytes: Long = Long.MAX_VALUE,
) {
    private val fileSize = Files.size(file)
    private val origin = minOf(maxOf(end, 0L), fileSize)
    private var chunk = ByteArray(0)
    private var chunkStart = origin
    private var unread = 0
    private var started = false
    private var finished = false
    private val pieces = ArrayDeque<ByteArray>()
    private var length = 0
    private var overflow = false
    private var discard = false

    /**
     * Start of the last line gone through (read, skipped or discarded); `end` before the first one. After a stop
     * inside a long line: the start of the last chunk read, a position in the middle of that line.
     */
    var boundary: Long = origin
        private set

    /** The next older line, or null at the start of the file or when the budget is spent. */
    fun next(): FileLine? {
        var found: FileLine? = null
        while (found == null && !finished) {
            if (unread == 0 && !refill()) break
            val newline = lastNewline()
            take(newline + 1)
            found = if (newline >= 0 || finished) complete(chunkStart + newline + 1) else null
            unread = maxOf(newline, 0)
        }
        return found
    }

    /**
     * Brings in the next older chunk; false when the budget is spent in the middle of a line that is skipped anyway
     * (too long, or the partial first one): the reader stops at [chunkStart], never reading the rest of that line.
     */
    private fun refill(): Boolean {
        val spent = origin - chunkStart >= budgetBytes
        return when {
            chunkStart <= 0 -> {
                finished = true
                true
            }

            spent && (overflow || discard) -> {
                finished = true
                boundary = chunkStart
                false
            }

            else -> {
                fill()
                true
            }
        }
    }

    private fun lastNewline(): Int {
        var index = unread - 1
        while (index >= 0 && chunk[index] != NEWLINE) index--
        return index
    }

    private fun fill() {
        val size = minOf(chunkBytes.toLong(), chunkStart).toInt()
        chunkStart -= size
        chunk =
            RandomAccessFile(file.toFile(), "r").use { raf ->
                ByteArray(size).also {
                    raf.seek(chunkStart)
                    raf.readFully(it)
                }
            }
        unread = size
        if (!started) {
            started = true
            discard = origin < fileSize && chunk[size - 1] != NEWLINE
        }
    }

    /** Adds the bytes from [from] to the unread end of the chunk to the current line. */
    private fun take(from: Int) {
        val count = unread - from
        if (count > 0 && !discard && !overflow) {
            length += count
            if (length > maxLineBytes) {
                overflow = true
                pieces.clear()
            } else {
                pieces.addFirst(chunk.copyOfRange(from, unread))
            }
        }
    }

    private fun complete(start: Long): FileLine? {
        val line = if (discard || overflow || length == 0) null else FileLine(start, decode())
        pieces.clear()
        length = 0
        overflow = false
        discard = false
        boundary = start
        if (origin - start >= budgetBytes) finished = true
        return line
    }

    private fun decode(): String {
        val bytes = ByteArrayOutputStream(length)
        pieces.forEach(bytes::write)
        return bytes.toString(Charsets.UTF_8)
    }

    private companion object {
        const val NEWLINE: Byte = '\n'.code.toByte()
    }
}
