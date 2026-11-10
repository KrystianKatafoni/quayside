package dev.quayside.agentruntime.impl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

/** Reading single entries (JSON lines) of a session file; every function is pure and total. */
internal object SessionEntries {
    private const val TITLE_MAX_CODE_POINTS = 80
    private val commandPrefixes = listOf("<command-", "<local-command-")
    private val blanks = Regex("\\s+")

    fun entriesOf(text: String): List<JsonObject> =
        text
            .lineSequence()
            .mapNotNull { line ->
                try {
                    Json.parseToJsonElement(line) as? JsonObject
                } catch (ignored: SerializationException) {
                    null
                } catch (ignored: IllegalArgumentException) {
                    null
                }
            }.toList()

    fun text(
        entry: JsonObject,
        key: String,
    ): String? = (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun absoluteCwd(entry: JsonObject): Path? {
        val cwd = text(entry, "cwd")
        return try {
            cwd?.let(Path::of)?.takeIf { it.isAbsolute }
        } catch (ignored: InvalidPathException) {
            null
        }
    }

    fun timestampOf(entry: JsonObject): String? = text(entry, "timestamp")

    fun instantOf(text: String): Instant? =
        try {
            Instant.parse(text)
        } catch (ignored: DateTimeParseException) {
            null
        }

    fun titleOf(entry: JsonObject): String? {
        val isUser = text(entry, "type") == "user" && entry["isMeta"] != JsonPrimitive(true)
        val message = (entry["message"] as? JsonObject)?.get("content")
        val line = if (isUser) firstText(message)?.replace(blanks, " ")?.trim() else null
        return line?.takeIf { it.isNotEmpty() && commandPrefixes.none { prefix -> it.startsWith(prefix) } }?.let(::cut)
    }

    private fun cut(line: String): String =
        if (line.codePointCount(0, line.length) <= TITLE_MAX_CODE_POINTS) {
            line
        } else {
            line.substring(0, line.offsetByCodePoints(0, TITLE_MAX_CODE_POINTS))
        }

    private fun firstText(content: JsonElement?): String? =
        when (content) {
            is JsonPrimitive -> content.takeIf { it.isString }?.content
            is JsonArray -> content.firstNotNullOfOrNull { (it as? JsonObject)?.let { part -> partText(part) } }
            else -> null
        }

    private fun partText(part: JsonObject): String? = if (text(part, "type") == "text") text(part, "text") else null
}
