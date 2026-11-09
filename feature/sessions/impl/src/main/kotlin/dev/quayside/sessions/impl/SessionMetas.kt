package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.process.GitBranch
import dev.quayside.sessions.api.SessionMeta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** Where the details panel gets model, permission mode and branch of a session (FR-S8, AM-818). */
internal fun interface SessionMetaSource {
    /** What is known about [session] now; unknown parts are null. Never throws except cancellation. */
    suspend fun read(session: RuntimeSession): SessionMeta
}

/** Knows nothing: every session has an empty [SessionMeta]. */
internal val NoSessionMetas: SessionMetaSource = SessionMetaSource { SessionMeta() }

/** [this] with every part that [fresh] knows replaced; a part [fresh] does not know keeps its last known value. */
internal fun SessionMeta.updatedBy(fresh: SessionMeta?): SessionMeta =
    if (fresh == null) {
        this
    } else {
        SessionMeta(
            model = fresh.model ?: model,
            permissionMode = fresh.permissionMode ?: permissionMode,
            branch = fresh.branch ?: branch,
        )
    }

/**
 * Model and permission mode come from the tail of the Claude Code transcript
 * (`<claudeHome>/projects/<cwd with every non-alphanumeric character as "-">/<conversation id>.jsonl`), the branch from
 * the `HEAD` of the work tree of the session directory ([GitBranch]). Both are plain file reads on [io]; a missing or
 * unreadable file is an unknown part, never an error. [claudeHome] null = no transcript is read.
 */
internal class FileSessionMetaSource(
    private val claudeHome: Path?,
    private val io: CoroutineDispatcher,
) : SessionMetaSource {
    override suspend fun read(session: RuntimeSession): SessionMeta =
        withContext(io) {
            val transcript = transcriptOf(session)?.let(TranscriptTail::read)
            SessionMeta(
                model = transcript?.model,
                permissionMode = transcript?.permissionMode,
                branch = GitBranch.read(session.cwd),
            )
        }

    private fun transcriptOf(session: RuntimeSession): Path? {
        val conversation = session.conversationId?.value?.takeIf { SAFE_FILE_NAME.matches(it) }
        val dir = session.cwd.toString().replace(NON_ALPHANUMERIC, "-")
        return conversation?.let { claudeHome?.resolve("projects")?.resolve(dir)?.resolve("$it.jsonl") }
    }

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9]")
        val SAFE_FILE_NAME = Regex("[A-Za-z0-9-]+")
    }
}

/** The newest model and permission mode found in a transcript. */
internal data class TranscriptTail(
    val model: String?,
    val permissionMode: String?,
) {
    internal companion object {
        /** Bytes read from the end of the file; older entries are not needed (the newest ones win). */
        const val TAIL_BYTES: Long = 256L * 1024
        private const val MAX_VALUE_CHARS = 80
        private val SAFE_VALUE = Regex("[A-Za-z0-9._:\\[\\]-]+")

        /** Reads the tail of [file]; null when it is missing or unreadable. Never throws. */
        fun read(file: Path): TranscriptTail? =
            try {
                if (file.isRegularFile()) parse(tail(file)) else null
            } catch (ignored: IOException) {
                null
            } catch (ignored: SecurityException) {
                null
            }

        /**
         * Pure: the newest `message.model` (an assistant entry; `<synthetic>` is no token and so skipped) and the
         * newest top-level `permissionMode` of [text], looking from the last line backwards. A line that is not a
         * JSON object (also the first one of a cut tail) is skipped, and so is a value that is not a plain token of
         * at most 80 characters.
         */
        fun parse(text: String): TranscriptTail {
            val entries =
                text
                    .lineSequence()
                    .toList()
                    .asReversed()
                    .mapNotNull(::objectOf)
            return TranscriptTail(
                model = entries.firstNotNullOfOrNull(::modelOf),
                permissionMode = entries.firstNotNullOfOrNull { token(it["permissionMode"]) },
            )
        }

        private fun tail(file: Path): String =
            RandomAccessFile(file.toFile(), "r").use { raf ->
                val start = (raf.length() - TAIL_BYTES).coerceAtLeast(minimumValue = 0)
                raf.seek(start)
                val bytes = ByteArray((raf.length() - start).toInt())
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }

        private fun objectOf(line: String): JsonObject? =
            if (line.isBlank()) {
                null
            } else {
                try {
                    Json.parseToJsonElement(line) as? JsonObject
                } catch (ignored: IllegalArgumentException) {
                    // SerializationException is an IllegalArgumentException; its message quotes the input.
                    null
                }
            }

        private fun modelOf(entry: JsonObject): String? = (entry["message"] as? JsonObject)?.get("model")?.let(::token)

        private fun token(value: JsonElement?): String? =
            (value as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.length <= MAX_VALUE_CHARS && SAFE_VALUE.matches(it) }
    }
}
