package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.core.domain.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Fixed categories of a settings file that cannot be used; never input text (parser messages quote it). */
internal object SettingsProblem {
    const val INVALID_JSON = "json:invalid"
    const val ROOT_NOT_OBJECT = "json:root-not-object"
    const val HOOKS_NOT_OBJECT = "json:hooks-not-object"
    const val EVENT_NOT_ARRAY = "json:event-not-array"
}

/**
 * Pure edits of Claude Code settings JSON (NFR-5, ADR 0010, ADR 0017). Only handlers that are ours are
 * added or removed; everything else in the file stays as it was, in the same order.
 */
internal object HookSettingsJson {
    /** Claude Code event names; spelled out so a renamed enum constant cannot change the file format. */
    private val EVENTS: Map<HookKind, String> =
        mapOf(
            HookKind.NOTIFICATION to "Notification",
            HookKind.STOP to "Stop",
            HookKind.STOP_FAILURE to "StopFailure",
            HookKind.SESSION_START to "SessionStart",
            HookKind.SESSION_END to "SessionEnd",
        )
    private const val TOKEN_HEADER = "X-AM-Token"
    private const val TOKEN_VARIABLE = HOOK_TOKEN_ENV
    private val PRETTY = Json { prettyPrint = true }

    /** Parses [text] to the root object; `Err` is a [SettingsProblem] category. */
    fun parse(text: String): Outcome<JsonObject, String> {
        val tree =
            try {
                Json.parseToJsonElement(text)
            } catch (ignored: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException; its message quotes the input.
                return Outcome.Err(SettingsProblem.INVALID_JSON)
            }
        val root = tree as? JsonObject
        return when {
            root == null -> Outcome.Err(SettingsProblem.ROOT_NOT_OBJECT)
            root["hooks"] != null && root["hooks"] !is JsonObject -> Outcome.Err(SettingsProblem.HOOKS_NOT_OBJECT)
            hasForeignEventShape(root) -> Outcome.Err(SettingsProblem.EVENT_NOT_ARRAY)
            else -> Outcome.Ok(root)
        }
    }

    // An event of ours that is not an array is the user's own value: we neither read it nor overwrite it.
    private fun hasForeignEventShape(root: JsonObject): Boolean {
        val hooks = root["hooks"] as? JsonObject ?: return false
        return EVENTS.values.any { event -> hooks[event]?.let { it !is JsonArray } == true }
    }

    /** Our hook kinds in [root] (already checked by [parse]). */
    fun kinds(root: JsonObject): Set<HookKind> {
        val hooks = root["hooks"] as? JsonObject ?: return emptySet()
        return EVENTS.filter { (_, event) -> hooks[event]?.let(OurHandlers::inGroups) == true }.keys
    }

    /** [root] with a handler of ours for every kind that has none yet; the same object when all are there. */
    fun withOurHooks(
        root: JsonObject,
        port: Int,
    ): JsonObject {
        val present = kinds(root)
        val missing = EVENTS.filterKeys { it !in present }
        if (missing.isEmpty()) return root
        val hooks = root["hooks"] as? JsonObject ?: JsonObject(emptyMap())
        val added = missing.values.associateWith { event -> appendGroup(hooks[event], handler(port)) }
        return JsonObject(root + ("hooks" to JsonObject(hooks + added)))
    }

    /** [root] without any handler of ours; groups, events and `hooks` that become empty are removed. */
    fun withoutOurHooks(root: JsonObject): JsonObject {
        val hooks = root["hooks"] as? JsonObject ?: return root
        val cleaned =
            hooks
                .mapNotNull { (event, groups) ->
                    if (EVENTS.containsValue(event)) cleanedEvent(groups)?.let { event to it } else event to groups
                }.toMap()
        return when {
            cleaned == hooks -> root
            cleaned.isEmpty() -> JsonObject(root - "hooks")
            else -> JsonObject(root + ("hooks" to JsonObject(cleaned)))
        }
    }

    fun render(root: JsonObject): String = PRETTY.encodeToString(JsonObject.serializer(), root) + "\n"

    // The shape of docs/03: the token is read from the environment of Claude Code, never written to the file.
    private fun handler(port: Int): JsonObject =
        buildJsonObject {
            put("type", "http")
            put("url", "http://127.0.0.1:$port/hooks")
            putJsonObject("headers") { put(TOKEN_HEADER, "\$$TOKEN_VARIABLE") }
            putJsonArray("allowedEnvVars") { add(JsonPrimitive(TOKEN_VARIABLE)) }
        }

    private fun appendGroup(
        existing: JsonElement?,
        handler: JsonObject,
    ): JsonElement {
        val group = buildJsonObject { put("hooks", buildJsonArray { add(handler) }) }
        val groups = (existing as? JsonArray).orEmpty() // [parse] rejected any other shape
        return JsonArray(groups + group)
    }

    /** The groups of one event without our handlers; null when nothing is left. */
    private fun cleanedEvent(groups: JsonElement): JsonElement? {
        val array = groups as? JsonArray ?: return groups
        val kept =
            array.mapNotNull { group ->
                val inner = (group as? JsonObject)?.get("hooks") as? JsonArray
                if (inner == null) {
                    group
                } else {
                    val rest = inner.filterNot { it is JsonObject && OurHandlers.isOurs(it) }
                    when {
                        rest.size == inner.size -> group
                        rest.isEmpty() -> null
                        else -> JsonObject(group + ("hooks" to JsonArray(rest)))
                    }
                }
            }
        return if (kept.isEmpty() && array.isNotEmpty()) null else JsonArray(kept)
    }
}
