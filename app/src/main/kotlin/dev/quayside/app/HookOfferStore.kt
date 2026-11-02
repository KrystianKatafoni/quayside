package dev.quayside.app

import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StorageError
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

/** File name of the hooks offer choice in the storage directory. */
internal const val HOOK_OFFER_FILE = "hook-offer.json"

/** `data` of `hook-offer.json`: `{"dismissed": true}` once the user skipped the offer or uninstalled (ADR 0020). */
internal data class OfferChoice(
    val dismissed: Boolean = false,
)

/** The store description of [HOOK_OFFER_FILE]: [OfferChoice], schema version 1, not dismissed by default. */
internal fun hookOfferSpec(): StoreSpec<OfferChoice> = StoreSpec(HOOK_OFFER_FILE, OfferChoiceSerializer, OfferChoice())

/**
 * Wire form of [OfferChoice]. A missing `dismissed` means not dismissed and unknown keys are skipped; a root that is
 * not an object or a `dismissed` that is not a boolean throws [SerializationException], so `JsonStore` moves the
 * file aside. Messages are fixed categories and never quote the input (ENG-C8).
 */
internal object OfferChoiceSerializer : KSerializer<OfferChoice> {
    private const val DISMISSED = "dismissed"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: OfferChoice,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(buildJsonObject { put(DISMISSED, value.dismissed) })
    }

    override fun deserialize(decoder: Decoder): OfferChoice {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("root is not an object")
        val element = root[DISMISSED] ?: return OfferChoice()
        val flag =
            (element as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: invalid("dismissed is not a boolean")
        return OfferChoice(dismissed = flag)
    }

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}

/** `hook-offer.json` (ADR 0020); missing or broken file = not dismissed. */
internal class HookOfferStore(
    storage: JsonStorage,
) {
    private val store = storage.store(hookOfferSpec())

    /** Whether the user skipped the offer or uninstalled before; any read problem means "not dismissed". */
    suspend fun dismissed(): Boolean =
        when (val loaded = store.load()) {
            is Outcome.Ok -> loaded.value.value.dismissed
            is Outcome.Err -> false
        }

    /** Remembers the choice. */
    suspend fun dismiss(): Outcome<Unit, StorageError> = store.save(OfferChoice(dismissed = true))
}
