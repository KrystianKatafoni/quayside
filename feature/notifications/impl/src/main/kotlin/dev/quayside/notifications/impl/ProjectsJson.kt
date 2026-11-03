package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The `projects` section of `notifications.json` (FR-N7): `{"<project id>": {"profile", "voice"}}`. The default
 * exception is not written. An unknown or empty profile is the default (the voice stays), a blank or null voice is
 * the global voice, a blank key is skipped, a default entry is dropped, a wrong type throws. Messages never quote
 * keys or voices (ENG-C8).
 */
internal object ProjectsJson {
    private const val PROFILE = "profile"
    private const val VOICE = "voice"
    private val PROFILES =
        mapOf(
            ProjectProfile.ERRORS_ONLY to "errorsOnly",
            ProjectProfile.MUTED to "muted",
        )

    fun encode(projects: Map<ProjectId, ProjectNotifications>): JsonObject =
        buildJsonObject {
            projects.filterValues { it != ProjectNotifications() }.forEach { (id, entry) ->
                put(id.value, entryJson(entry))
            }
        }

    fun decode(element: JsonElement?): Map<ProjectId, ProjectNotifications> {
        if (element == null) return emptyMap()
        val obj = element as? JsonObject ?: invalid("projects is not an object")
        return obj.entries
            .filter { it.key.isNotBlank() }
            .associate { (id, value) -> ProjectId(id) to entry(value) }
            .filterValues { it != ProjectNotifications() }
    }

    private fun entryJson(entry: ProjectNotifications): JsonObject =
        buildJsonObject {
            PROFILES[entry.profile]?.let { put(PROFILE, it) }
            entry.voice?.let { put(VOICE, it.name) }
        }

    private fun entry(element: JsonElement): ProjectNotifications {
        val obj = element as? JsonObject ?: invalid("a project entry is not an object")
        return ProjectNotifications(profile(obj[PROFILE]), voice(obj[VOICE]))
    }

    private fun profile(element: JsonElement?): ProjectProfile {
        if (element == null) return ProjectProfile.DEFAULT
        val text = text(element, "bad profile")
        return PROFILES.entries.firstOrNull { it.value == text }?.key ?: ProjectProfile.DEFAULT
    }

    private fun voice(element: JsonElement?): VoiceId? {
        if (element == null || element is JsonNull) return null
        return text(element, "bad voice").takeIf { it.isNotBlank() }?.let(::VoiceId)
    }

    private fun text(
        element: JsonElement,
        reason: String,
    ): String = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid(reason)
}
