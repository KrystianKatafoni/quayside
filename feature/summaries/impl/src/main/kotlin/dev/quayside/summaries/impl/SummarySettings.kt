package dev.quayside.summaries.impl

import dev.quayside.core.storage.StoreSpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** File name of the summaries settings in the storage directory. */
internal const val SUMMARIES_FILE = "summaries.json"

/**
 * `data` of `summaries.json`: `{"useModel": false}`. Off by default, because a summary by a model costs tokens
 * and sends the message to the model a second time (ADR 0019).
 */
internal data class SummarySettings(
    val useModel: Boolean = false,
)

/** The store description of [SUMMARIES_FILE]: [SummarySettings], schema version 1, default model off. */
internal fun summarySettingsSpec(): StoreSpec<SummarySettings> =
    StoreSpec(SUMMARIES_FILE, SummarySettingsSerializer, SummarySettings())

/**
 * Wire form of [SummarySettings]. A missing `useModel` means off and unknown keys are skipped; a root that is not
 * an object or a `useModel` that is not a boolean throws [SerializationException], so `JsonStore` moves the file
 * aside and the model stays off. Messages are fixed categories and never quote the input (ENG-C8).
 */
internal object SummarySettingsSerializer : KSerializer<SummarySettings> {
    private const val USE_MODEL = "useModel"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: SummarySettings,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(buildJsonObject { put(USE_MODEL, value.useModel) })
    }

    override fun deserialize(decoder: Decoder): SummarySettings {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("root is not an object")
        val element = root[USE_MODEL] ?: return SummarySettings()
        val flag =
            (element as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: invalid("useModel is not a boolean")
        return SummarySettings(useModel = flag)
    }

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}
