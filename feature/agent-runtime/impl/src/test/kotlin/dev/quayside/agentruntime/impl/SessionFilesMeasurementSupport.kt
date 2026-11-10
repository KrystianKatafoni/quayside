package dev.quayside.agentruntime.impl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** What the first `user` entry of a file looks like (M7); the text itself is never kept. */
internal enum class FirstUser { COMMAND, META, CAVEAT, TEXT, NONE }

/**
 * Facts about one session file for the aggregate report of AM-311. Holds directory names and `cwd` strings
 * (needed to compare encodings), but the report never prints a path, a directory name, a UUID or a text:
 * only counts, sizes, times and sanitized entry type names.
 */
internal data class FileFacts(
    val dirName: String,
    val size: Long,
    val agentFileName: Boolean,
    val firstTypes: List<String>,
    val firstCwd: String?,
    val lastCwd: String?,
    val distinctCwds: Int,
    val firstEntryHasCwd: Boolean,
    val idMatchesStem: Boolean,
    val sidechain: Boolean,
    val firstUser: FirstUser,
    val hasSummary: Boolean,
)

internal object FileFactsReader {
    const val CHUNK = 64 * 1024
    private val typeName = Regex("[a-z][a-z0-9-]{0,30}")

    /** Reads the head and the tail of [file] (at most [CHUNK] bytes each) and derives the facts. */
    fun read(
        file: Path,
        dirName: String,
    ): FileFacts {
        val size = file.fileSize()
        val whole = size <= CHUNK
        val head = entries(chunk(file, 0, minOf(size, CHUNK.toLong()).toInt()), dropFirst = false, dropLast = !whole)
        val tail =
            if (whole) emptyList() else entries(chunk(file, size - CHUNK, CHUNK), dropFirst = true, dropLast = false)
        return factsOf(file, dirName, size, head, head + tail)
    }

    private fun factsOf(
        file: Path,
        dirName: String,
        size: Long,
        head: List<JsonObject>,
        all: List<JsonObject>,
    ): FileFacts {
        val cwds = all.mapNotNull(::absoluteCwd)
        return FileFacts(
            dirName = dirName,
            size = size,
            agentFileName = file.name.startsWith("agent-"),
            firstTypes = head.take(FIRST_ENTRIES).map { sanitizedType(it) },
            firstCwd = cwds.firstOrNull(),
            lastCwd = cwds.lastOrNull(),
            distinctCwds = cwds.toSet().size,
            firstEntryHasCwd = head.firstOrNull()?.let(::absoluteCwd) != null,
            idMatchesStem = all.firstNotNullOfOrNull { text(it, "sessionId") } == file.name.removeSuffix(".jsonl"),
            sidechain = head.firstOrNull()?.get("isSidechain") == JsonPrimitive(true),
            firstUser = firstUser(head),
            hasSummary = all.any { text(it, "type") == "summary" },
        )
    }

    private fun firstUser(entries: List<JsonObject>): FirstUser {
        val user = entries.firstOrNull { text(it, "type") == "user" } ?: return FirstUser.NONE
        val content = (user["message"] as? JsonObject)?.get("content")
        val text = firstText(content).orEmpty()
        return when {
            user["isMeta"] == JsonPrimitive(true) -> FirstUser.META
            text.startsWith("<command-name>") -> FirstUser.COMMAND
            text.startsWith("Caveat:") -> FirstUser.CAVEAT
            else -> FirstUser.TEXT
        }
    }

    private fun firstText(content: JsonElement?): String? =
        when (content) {
            is JsonPrimitive -> content.takeIf { it.isString }?.content
            is JsonArray -> content.firstNotNullOfOrNull { (it as? JsonObject)?.let { part -> text(part, "text") } }
            else -> null
        }

    private fun sanitizedType(entry: JsonObject): String = text(entry, "type")?.takeIf(typeName::matches) ?: "other"

    private fun absoluteCwd(entry: JsonObject): String? = text(entry, "cwd")?.takeIf { it.startsWith("/") }

    private fun text(
        entry: JsonObject,
        key: String,
    ): String? = (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun chunk(
        file: Path,
        from: Long,
        length: Int,
    ): String =
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val bytes = ByteArray(length)
            raf.seek(from)
            raf.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }

    private fun entries(
        text: String,
        dropFirst: Boolean,
        dropLast: Boolean,
    ): List<JsonObject> {
        val lines = text.lines().let { if (dropFirst) it.drop(1) else it }.let { if (dropLast) it.dropLast(1) else it }
        return lines.mapNotNull { line ->
            try {
                Json.parseToJsonElement(line) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    private const val FIRST_ENTRIES = 5
}

/** Percentile [p] (0..100) of [values], nearest rank; 0 for an empty list. */
internal fun percentile(
    values: List<Long>,
    p: Int,
): Long {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[((sorted.size - 1) * p / 100.0).toInt().coerceIn(0, sorted.size - 1)]
}

internal fun ratio(
    part: Int,
    total: Int,
): String = if (total == 0) "0/0" else "$part/$total (${part * 100 / total}%)"
