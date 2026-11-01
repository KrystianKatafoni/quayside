package dev.quayside.agentruntime.impl

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URISyntaxException

/** Recognition of our hook handlers in settings JSON (ADR 0010); pure. */
internal object OurHandlers {
    private const val TOKEN_HEADER = "X-AM-Token"
    private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost")

    /** `<event>: [{"hooks": [handler, …]}, …]` with at least one handler of ours. */
    fun inGroups(groups: JsonElement): Boolean =
        (groups as? JsonArray)
            .orEmpty()
            .flatMap { group -> ((group as? JsonObject)?.get("hooks") as? JsonArray).orEmpty() }
            .any { handler -> handler is JsonObject && isOurs(handler) }

    /** ADR 0010: `type` http, URL `http://127.0.0.1|localhost:<any port>/hooks`, header `X-AM-Token`. */
    fun isOurs(handler: JsonObject): Boolean =
        handler.text("type") == "http" &&
            handler.text("url")?.let(::isOurUrl) == true &&
            (handler["headers"] as? JsonObject)?.keys.orEmpty().any { it.equals(TOKEN_HEADER, ignoreCase = true) }

    private fun isOurUrl(url: String): Boolean {
        val uri =
            try {
                URI(url)
            } catch (ignored: URISyntaxException) {
                return false
            }
        return uri.scheme.equals("http", ignoreCase = true) &&
            uri.host?.lowercase() in LOCAL_HOSTS &&
            uri.path == "/hooks"
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
