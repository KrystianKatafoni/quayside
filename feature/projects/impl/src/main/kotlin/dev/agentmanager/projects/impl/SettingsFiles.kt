package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.catching
import dev.agentmanager.core.domain.getOrElse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** What reading one `.claude/settings*.json` file gave. */
internal sealed class SettingsRead {
    internal data object Missing : SettingsRead()

    internal data class Text(
        val text: String,
    ) : SettingsRead()

    /** [reason] is a fixed category, never a path or an exception message. */
    internal data class Unreadable(
        val reason: String,
    ) : SettingsRead()
}

/** Read-only access to settings files. */
internal object SettingsFiles {
    const val MAX_BYTES: Int = 1 shl 20

    const val NOT_REGULAR: String = "file:not-regular"
    const val TOO_LARGE: String = "file:too-large"
    const val INVALID_UTF8: String = "file:invalid-utf8"
    const val IO_FAILED: String = "file:io"
    const val DENIED: String = "file:security"

    /**
     * Reads [file] on [io] without locks or writes (symlinks followed). No file → [SettingsRead.Missing];
     * not a regular file, over [MAX_BYTES], not UTF-8 or any IO problem → [SettingsRead.Unreadable].
     */
    suspend fun read(
        file: Path,
        io: CoroutineDispatcher,
    ): SettingsRead =
        withContext(io) {
            catching(::failure) { readChecked(file) }.getOrElse { it }
        }

    private fun readChecked(file: Path): SettingsRead {
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        // Read one byte past the limit: the file may grow between the size check and the read.
        val bytes =
            if (attributes.isRegularFile) {
                Files
                    .newInputStream(
                        file,
                    ).use { it.readNBytes(MAX_BYTES + 1) }
            } else {
                null
            }
        return when {
            bytes == null -> SettingsRead.Unreadable(NOT_REGULAR)
            attributes.size() > MAX_BYTES || bytes.size > MAX_BYTES -> SettingsRead.Unreadable(TOO_LARGE)
            else -> SettingsRead.Text(decodeUtf8(bytes))
        }
    }

    /** Exception → fixed category; the message (it may hold the path) is dropped (ENG-C8). */
    private fun failure(e: Exception): SettingsRead =
        when (e) {
            is NoSuchFileException -> SettingsRead.Missing
            is CharacterCodingException -> SettingsRead.Unreadable(INVALID_UTF8)
            is SecurityException -> SettingsRead.Unreadable(DENIED)
            else -> SettingsRead.Unreadable(IO_FAILED)
        }

    /** Strict UTF-8: malformed input throws [CharacterCodingException] instead of becoming U+FFFD. */
    private fun decodeUtf8(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
}
