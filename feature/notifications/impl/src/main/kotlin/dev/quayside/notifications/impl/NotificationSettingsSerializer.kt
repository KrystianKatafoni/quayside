package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `data` of `notifications.json`:
 * `{"popups": true, "voice": true, "runningLongMinutes": 30, "channels": {"needsInput": ["popup", …]}}`.
 *
 * The wire names are spelled out here so renaming an enum constant cannot change the file. Every key is
 * optional (a missing one means the default); unknown keys, kinds and channels are skipped so a newer
 * version's additions survive a downgrade of the reader. A value of the wrong type throws
 * [SerializationException], so `JsonStore` moves the file aside; an out-of-range threshold is clamped.
 * Messages are fixed categories and never quote the input (ENG-C8).
 */
internal object NotificationSettingsSerializer : KSerializer<NotificationSettings> {
    private val KINDS =
        mapOf(
            NotificationKind.NEEDS_INPUT to "needsInput",
            NotificationKind.PERMISSION to "permission",
            NotificationKind.TURN_ENDED to "turnEnded",
            NotificationKind.FAILED to "failed",
            NotificationKind.PULL_REQUEST to "pullRequest",
            NotificationKind.RUNNING_LONG to "runningLong",
            NotificationKind.TERMINAL_BELL to "terminalBell",
        )
    private val CHANNELS =
        mapOf(
            NotificationChannel.POPUP to "popup",
            NotificationChannel.VOICE to "voice",
            NotificationChannel.SOUND to "sound",
            NotificationChannel.DOCK_BADGE to "dockBadge",
        )
    private val POSITIONS = PopupPosition.entries.associateWith { it.name }
    private val DURATIONS = PopupDuration.entries.associateWith { it.name }
    private val STYLES = PopupStyle.entries.associateWith { it.name }
    private const val POPUP = "popup"
    private const val POPUPS = "popups"
    private const val VOICE = "voice"
    private const val MINUTES = "runningLongMinutes"
    private const val CHANNELS_KEY = "channels"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: NotificationSettings,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(
            buildJsonObject {
                put(POPUPS, value.popups)
                put(VOICE, value.voice)
                put(MINUTES, value.runningLongMinutes)
                putJsonObject(POPUP) {
                    put("position", value.popup.position.name)
                    put("duration", value.popup.duration.name)
                    put("maxVisible", value.popup.maxVisible)
                    put("style", value.popup.style.name)
                }
                putJsonObject(CHANNELS_KEY) {
                    KINDS.forEach { (kind, name) ->
                        putJsonArray(
                            name,
                        ) { value.channelsFor(kind).forEach { add(JsonPrimitive(CHANNELS.getValue(it))) } }
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): NotificationSettings {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("root is not an object")
        val defaults = NotificationSettings()
        return NotificationSettings(
            popups = root.flag(POPUPS) ?: defaults.popups,
            voice = root.flag(VOICE) ?: defaults.voice,
            matrix = matrix(root[CHANNELS_KEY]),
            popup = popup(root[POPUP]),
            runningLongMinutes =
                root.number(MINUTES)?.coerceIn(
                    NotificationSettings.MIN_RUNNING_LONG_MINUTES,
                    NotificationSettings.MAX_RUNNING_LONG_MINUTES,
                ) ?: defaults.runningLongMinutes,
        )
    }

    private fun popup(element: JsonElement?): PopupSettings {
        val defaults = PopupSettings()
        if (element == null) return defaults
        val obj = element as? JsonObject ?: invalid("$POPUP is not an object")
        return PopupSettings(
            position = obj.choice("position", POSITIONS, defaults.position),
            duration = obj.choice("duration", DURATIONS, defaults.duration),
            maxVisible =
                obj.number("maxVisible")?.coerceIn(1, PopupSettings.MAX_VISIBLE_LIMIT) ?: defaults.maxVisible,
            style = obj.choice("style", STYLES, defaults.style),
        )
    }

    /** The value named by [key], [default] when the key is missing or names something unknown. */
    private fun <E> JsonObject.choice(
        key: String,
        names: Map<E, String>,
        default: E,
    ): E {
        val primitive = get(key) ?: return default
        val text = (primitive as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("bad $key")
        return names.entries.firstOrNull { it.value == text }?.key ?: default
    }

    private fun matrix(element: JsonElement?): Map<NotificationKind, Set<NotificationChannel>> {
        if (element == null) return emptyMap()
        val channels = element as? JsonObject ?: invalid("$CHANNELS_KEY is not an object")
        return KINDS
            .mapNotNull { (kind, name) -> channels[name]?.let { kind to channelSet(it) } }
            .toMap()
    }

    private fun channelSet(element: JsonElement): Set<NotificationChannel> {
        val names = element as? JsonArray ?: invalid("a kind is not an array")
        return names
            .map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: invalid("a channel is not text") }
            .mapNotNull { name -> CHANNELS.entries.firstOrNull { it.value == name }?.key }
            .toSet()
    }

    private fun JsonObject.flag(key: String): Boolean? =
        get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull ?: invalid("bad $key") }

    private fun JsonObject.number(key: String): Int? =
        get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull ?: invalid("bad $key") }

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}
