package dev.quayside.app

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put

/** File name of the window layout in the storage directory (ADR 0025). */
internal const val WINDOW_FILE = "window.json"

/** `data` of `window.json` (ADR 0025): the terminal height of the list mode in dp. */
internal data class WindowLayout(
    val terminalHeight: Float = DEFAULT_TERMINAL_HEIGHT.value,
)

/** The store description of [WINDOW_FILE]: [WindowLayout], schema version 1, the default height by default. */
internal fun windowLayoutSpec(): StoreSpec<WindowLayout> =
    StoreSpec(WINDOW_FILE, WindowLayoutSerializer, WindowLayout())

/**
 * Wire form of [WindowLayout] (ADR 0025): only a deviation from the default height is written. A missing key, a
 * string, a non-number, a non-finite number or a value <= 0 reads as the default; a root that is not an object throws
 * [SerializationException], so `JsonStore` moves the file aside. Messages never quote the input (ENG-C8).
 */
internal object WindowLayoutSerializer : KSerializer<WindowLayout> {
    private const val HEIGHT = "terminalHeight"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: WindowLayout,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(
            buildJsonObject {
                if (value.terminalHeight != WindowLayout().terminalHeight) put(HEIGHT, value.terminalHeight)
            },
        )
    }

    override fun deserialize(decoder: Decoder): WindowLayout {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("root is not an object")
        val height =
            (root[HEIGHT] as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.floatOrNull
                ?.takeIf { it.isFinite() && it > 0f }
        return height?.let { WindowLayout(it) } ?: WindowLayout()
    }

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}

/** `window.json` (ADR 0025): lenient read, only deviations from [WindowLayout] defaults are written. */
internal class WindowLayoutStore(
    storage: JsonStorage,
    private val logger: Logger,
) {
    private val store = storage.store(windowLayoutSpec())

    /** The stored layout; any read problem (broken file, newer schema, IO) gives the defaults and one WARN. */
    suspend fun load(): WindowLayout =
        when (val loaded = store.load()) {
            is Outcome.Ok -> {
                if (loaded.value.recovered != null) logger.warn("window layout unreadable", "reason" to "broken")
                loaded.value.value
            }

            is Outcome.Err -> {
                logger.warn("window layout unreadable", "reason" to loaded.error::class.simpleName)
                WindowLayout()
            }
        }

    /** Writes [layout]; a height equal to the default leaves no key in the file. */
    suspend fun save(layout: WindowLayout): Outcome<Unit, StorageError> = store.save(layout)
}

/** The layout store and what [WindowLayoutStore.load] gave in `main`, before the window opened. */
internal class WindowLayoutDeps(
    val store: WindowLayoutStore,
    val initial: WindowLayout,
)
