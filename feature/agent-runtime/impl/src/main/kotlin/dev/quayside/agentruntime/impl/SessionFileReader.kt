package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Path

/**
 * Reads at most [headBytes] from the start and [tailBytes] from the end of a session file on [io]
 * and turns them into a [SessionFileSummary]. A file no larger than [headBytes] is read whole.
 */
internal class SessionFileReader(
    private val io: CoroutineDispatcher,
    private val headBytes: Int = DEFAULT_BYTES,
    private val tailBytes: Int = DEFAULT_BYTES,
) {
    /** Never throws except cancellation; IO failures become [SessionFileProblem.UNREADABLE]. */
    suspend fun read(file: Path): Outcome<SessionFileSummary, SessionFileProblem> =
        withContext(io) {
            try {
                readChunks(file)
            } catch (ignored: IOException) {
                Outcome.Err(SessionFileProblem.UNREADABLE)
            }
        }

    private fun readChunks(file: Path): Outcome<SessionFileSummary, SessionFileProblem> {
        val stem = file.fileName.toString().removeSuffix(".jsonl")
        return RandomAccessFile(file.toFile(), "r").use { raf ->
            val size = raf.length()
            val whole = size <= headBytes
            val head = chunk(raf, 0, minOf(size, headBytes.toLong()).toInt())
            val tailLength = minOf(size, tailBytes.toLong()).toInt()
            val tail = if (whole) "" else chunk(raf, size - tailLength, tailLength)
            SessionFiles.parse(stem, head, tail, whole)
        }
    }

    private fun chunk(
        raf: RandomAccessFile,
        from: Long,
        length: Int,
    ): String {
        val bytes = ByteArray(length)
        raf.seek(from)
        raf.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val DEFAULT_BYTES = 64 * 1024
    }
}
