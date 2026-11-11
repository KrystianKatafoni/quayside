package dev.quayside.sessions.impl

import dev.quayside.core.domain.SessionId
import dev.quayside.core.storage.StoreSpec
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.SessionHistorySettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** File name of the disk sessions settings in the storage directory (ADR 0032). */
internal const val SESSION_HISTORY_FILE = "session-history.json"

/** The store description of [SESSION_HISTORY_FILE]: [SessionHistorySettings], schema version 1. */
internal fun sessionHistorySettingsSpec(): StoreSpec<SessionHistorySettings> =
    StoreSpec(SESSION_HISTORY_FILE, SessionHistorySettingsSerializer, SessionHistorySettings())

/**
 * Wire form of [SessionHistorySettings]: only deviations from the defaults are written (ADR 0024). `maxAge` is the
 * name of a [HistoryAge], `maxPerProject` the number of a [HistoryLimit], `hidden` the sorted conversation UUIDs,
 * `places` the entries `{project, subfolder?}` by sorted key (ADR 0033; no `subfolder` = the project root).
 * Unknown keys are skipped; a value we do not know or of the wrong type throws [SerializationException], so
 * `JsonStore` moves the file aside. Messages are fixed categories and never quote the input (ENG-C8).
 */
internal object SessionHistorySettingsSerializer : KSerializer<SessionHistorySettings> {
    private const val MAX_AGE = "maxAge"
    private const val MAX_PER_PROJECT = "maxPerProject"
    private const val HIDDEN = "hidden"
    private const val PLACES = "places"
    private val defaults = SessionHistorySettings()

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: SessionHistorySettings,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(JsonObject(deviations(value)))
    }

    private fun deviations(value: SessionHistorySettings): Map<String, JsonElement> =
        buildMap {
            if (value.maxAge != defaults.maxAge) put(MAX_AGE, JsonPrimitive(value.maxAge.name))
            if (value.maxPerProject != defaults.maxPerProject) {
                put(MAX_PER_PROJECT, JsonPrimitive(value.maxPerProject.count))
            }
            if (value.hidden.isNotEmpty()) {
                put(
                    HIDDEN,
                    JsonArray(
                        value.hidden
                            .map { it.value }
                            .sorted()
                            .map(::JsonPrimitive),
                    ),
                )
            }
            if (value.places.isNotEmpty()) put(PLACES, SessionPlacesJson.encode(value.places))
        }

    override fun deserialize(decoder: Decoder): SessionHistorySettings {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("root is not an object")
        return SessionHistorySettings(
            maxAge = root[MAX_AGE]?.let(::age) ?: defaults.maxAge,
            maxPerProject = root[MAX_PER_PROJECT]?.let(::limit) ?: defaults.maxPerProject,
            hidden = root[HIDDEN]?.let(::hidden) ?: defaults.hidden,
            places = root[PLACES]?.let(SessionPlacesJson::decode) ?: defaults.places,
        )
    }

    private fun age(element: JsonElement): HistoryAge {
        val name = text(element) ?: invalid("maxAge is not a string")
        return HistoryAge.entries.firstOrNull { it.name == name } ?: invalid("maxAge is unknown")
    }

    private fun limit(element: JsonElement): HistoryLimit {
        val count =
            (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: invalid("maxPerProject is not a number")
        return HistoryLimit.entries.firstOrNull { it.count == count } ?: invalid("maxPerProject is unknown")
    }

    private fun hidden(element: JsonElement): Set<SessionId> {
        val array = element as? JsonArray ?: invalid("hidden is not an array")
        return array.map { SessionId(text(it)?.takeIf(String::isNotBlank) ?: invalid("hidden holds a bad id")) }.toSet()
    }

    private fun text(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}
