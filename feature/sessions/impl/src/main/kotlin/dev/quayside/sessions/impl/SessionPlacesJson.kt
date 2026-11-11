package dev.quayside.sessions.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.StoredPlace
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire form of the `places` key of `session-history.json` (AM-314, ADR 0033): `{key: {project, subfolder?}}` sorted by
 * key; no `subfolder` = the project root. A bad entry throws [SerializationException] with a fixed message that never
 * quotes the input (ENG-C8), so `JsonStore` moves the file aside.
 */
internal object SessionPlacesJson {
    private const val PROJECT = "project"
    private const val SUBFOLDER = "subfolder"

    fun encode(places: Map<SessionId, StoredPlace>): JsonObject =
        JsonObject(places.entries.sortedBy { it.key.value }.associate { (key, stored) -> key.value to entry(stored) })

    fun decode(element: JsonElement): Map<SessionId, StoredPlace> {
        val entries = element as? JsonObject ?: invalid("places is not an object")
        return entries.entries.associate { (key, value) -> SessionId(key) to stored(value) }
    }

    private fun entry(stored: StoredPlace): JsonObject =
        JsonObject(
            buildMap {
                put(PROJECT, JsonPrimitive(stored.project.value))
                (stored.place as? PlaceAssignment.Subfolder)?.let { put(SUBFOLDER, JsonPrimitive(it.name)) }
            },
        )

    private fun stored(element: JsonElement): StoredPlace {
        val entry = element as? JsonObject ?: invalid("a place is not an object")
        val project = entry[PROJECT]?.let(::text)?.takeIf(String::isNotBlank) ?: invalid("a place has no project")
        val subfolder = entry[SUBFOLDER]?.let { text(it) ?: invalid("a subfolder is not a string") }
        val assignment = if (subfolder == null) PlaceAssignment.ProjectRoot else subfolder(subfolder)
        return StoredPlace(ProjectId(project), assignment)
    }

    private fun subfolder(name: String): PlaceAssignment.Subfolder {
        val bad = name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' }
        if (bad) invalid("a subfolder is not a direct child name")
        return PlaceAssignment.Subfolder(name)
    }

    private fun text(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}
