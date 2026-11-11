package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure rules that turn one line of a session file into a message (ADR 0034). */
internal object ConversationEntries {
    const val MAX_TEXT_CODE_POINTS: Int = 4_000
    private val commandPrefixes = listOf("<command-", "<local-command-")

    /**
     * The message of [line], or null for anything else. Only `user` and `assistant` entries, only `text` parts
     * joined with `\n` (or a string `content`); not `isMeta`, `isSidechain`, commands (`<command-`,
     * `<local-command-`), `tool_result`, `tool_use` or `thinking`. Total: broken JSON gives null. `at` is null
     * without a valid `timestamp`. Text over [MAX_TEXT_CODE_POINTS] code points is cut and `truncated`.
     */
    fun messageOf(line: String): ConversationMessage? = SessionEntries.objectOf(line)?.let(::messageOfEntry)

    private fun messageOfEntry(entry: JsonObject): ConversationMessage? {
        val role = roleOf(entry)
        val text = if (role == null || isHidden(entry)) null else textOf(entry)
        return if (role == null || text == null) {
            null
        } else {
            val at = SessionEntries.timestampOf(entry)?.let(SessionEntries::instantOf)
            val over = text.codePointCount(0, text.length) > MAX_TEXT_CODE_POINTS
            ConversationMessage(role, SessionEntries.cut(text, MAX_TEXT_CODE_POINTS), at, over)
        }
    }

    private fun roleOf(entry: JsonObject): ConversationRole? =
        when (SessionEntries.text(entry, "type")) {
            "user" -> ConversationRole.USER
            "assistant" -> ConversationRole.ASSISTANT
            else -> null
        }

    private fun isHidden(entry: JsonObject): Boolean =
        entry["isMeta"] == JsonPrimitive(true) || entry["isSidechain"] == JsonPrimitive(true)

    private fun textOf(entry: JsonObject): String? {
        val content = (entry["message"] as? JsonObject)?.get("content")
        val joined = if (content == null) null else textParts(content).joinToString("\n")
        return joined?.takeIf { it.isNotBlank() && !isCommand(it) }
    }

    private fun isCommand(text: String): Boolean = commandPrefixes.any { text.trimStart().startsWith(it) }

    private fun textParts(content: JsonElement): List<String> =
        when (content) {
            is JsonPrimitive -> listOfNotNull(content.takeIf { it.isString }?.content)
            is JsonArray -> content.mapNotNull { (it as? JsonObject)?.let(::partText) }
            else -> emptyList()
        }

    private fun partText(part: JsonObject): String? =
        if (SessionEntries.text(part, "type") == "text") SessionEntries.text(part, "text") else null
}
