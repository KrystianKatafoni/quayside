package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.flatMap
import dev.quayside.projects.api.HooksPresence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** Pure recognition of Quayside hooks in settings JSON (ADR 0010). */
internal object OurHooks {
    const val INVALID_JSON: String = "json:invalid"
    const val ROOT_NOT_OBJECT: String = "json:root-not-object"
    const val HOOKS_NOT_OBJECT: String = "json:hooks-not-object"

    /** Claude Code event names; spelled out so a renamed enum constant cannot change the file format. */
    private val EVENTS: Map<String, HookKind> =
        mapOf(
            "Notification" to HookKind.NOTIFICATION,
            "Stop" to HookKind.STOP,
            "StopFailure" to HookKind.STOP_FAILURE,
            "SessionStart" to HookKind.SESSION_START,
            "SessionEnd" to HookKind.SESSION_END,
        )
    private const val TOKEN_HEADER = "X-AM-Token"
    private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost")

    /**
     * Our hook kinds in [settingsText]. Invalid JSON, a root that is not an object or `hooks` that is not
     * an object → `Err` with a fixed category, never input text (parser messages quote the input). Deeper
     * deviations from the shape (an event that is not an array, a handler that is not an object) are skipped.
     */
    fun kinds(settingsText: String): Outcome<Set<HookKind>, String> =
        catching<JsonElement, String>({ INVALID_JSON }) { Json.parseToJsonElement(settingsText) }
            .flatMap { root -> hooksOf(root) }
            .flatMap { hooks -> Outcome.Ok(kindsIn(hooks)) }

    /** Our kinds of one read file; [SettingsRead.Unreadable] → `Err` with its category. */
    fun kindsOf(read: SettingsRead): Outcome<Set<HookKind>, String> =
        when (read) {
            SettingsRead.Missing -> Outcome.Ok(emptySet())
            is SettingsRead.Text -> kinds(read.text)
            is SettingsRead.Unreadable -> Outcome.Err(read.reason)
        }

    /** Combines both settings files, see [HooksPresence]. */
    fun presence(reads: List<SettingsRead>): HooksPresence = presenceOf(reads.map(::kindsOf))

    /** Any `Err` → [HooksPresence.UNREADABLE]; otherwise the union of kinds decides. */
    fun presenceOf(results: List<Outcome<Set<HookKind>, String>>): HooksPresence {
        if (results.any { it is Outcome.Err }) return HooksPresence.UNREADABLE
        val found = results.flatMap { (it as? Outcome.Ok)?.value.orEmpty() }.toSet()
        return when {
            found.isEmpty() -> HooksPresence.NONE
            found.containsAll(HookKind.entries) -> HooksPresence.INSTALLED
            else -> HooksPresence.PARTIAL
        }
    }

    private fun hooksOf(root: JsonElement): Outcome<JsonObject, String> {
        if (root !is JsonObject) return Outcome.Err(ROOT_NOT_OBJECT)
        return when (val hooks = root["hooks"]) {
            null -> Outcome.Ok(JsonObject(emptyMap()))
            is JsonObject -> Outcome.Ok(hooks)
            else -> Outcome.Err(HOOKS_NOT_OBJECT)
        }
    }

    private fun kindsIn(hooks: JsonObject): Set<HookKind> =
        hooks.entries
            .mapNotNull { (event, groups) -> EVENTS[event]?.takeIf { hasOurHandler(groups) } }
            .toSet()

    /** `<event>: [{"hooks": [handler, …]}, …]` with at least one handler of ours. */
    private fun hasOurHandler(groups: JsonElement): Boolean =
        (groups as? JsonArray)
            .orEmpty()
            .flatMap { group -> ((group as? JsonObject)?.get("hooks") as? JsonArray).orEmpty() }
            .any { handler -> handler is JsonObject && isOurs(handler) }

    private fun isOurs(handler: JsonObject): Boolean =
        handler.text("type") == "http" &&
            handler.text("url")?.let(::isOurUrl) == true &&
            (handler["headers"] as? JsonObject)?.keys.orEmpty().any { it.equals(TOKEN_HEADER, ignoreCase = true) }

    /** `http://127.0.0.1|localhost:<any port>/hooks`. */
    private fun isOurUrl(url: String): Boolean {
        val uri = catching<URI, Unit>({}) { URI(url) }
        return uri is Outcome.Ok &&
            uri.value.scheme.equals("http", ignoreCase = true) &&
            uri.value.host?.lowercase() in LOCAL_HOSTS &&
            uri.value.path == "/hooks"
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
