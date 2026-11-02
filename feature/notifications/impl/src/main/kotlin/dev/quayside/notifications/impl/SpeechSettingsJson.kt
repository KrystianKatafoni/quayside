package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The `speech` section of `notifications.json`. A missing key is the default; `rate` and `volume` are clamped; a
 * blank voice or template is the default; JSON `null` for `voice` is the system voice; a wrong type throws.
 */
internal object SpeechSettingsJson {
    private const val MAX_VOLUME = 100

    fun encode(speech: SpeechSettings): JsonObject =
        buildJsonObject {
            put("voice", speech.voice?.name)
            put("rate", speech.rate)
            put("volume", speech.volume)
            putJsonObject("templates") {
                speech.templates.forEach { (kind, text) ->
                    put(NotificationSettingsSerializer.KINDS.getValue(kind), text)
                }
            }
            put("readQuestion", speech.readQuestion)
            put("onlyInBackground", speech.onlyInBackground)
            put("muteWhenMicrophoneActive", speech.muteWhenMicrophoneActive)
        }

    fun decode(element: JsonElement?): SpeechSettings {
        val defaults = SpeechSettings()
        if (element == null) return defaults
        val obj = element as? JsonObject ?: invalid("speech is not an object")
        return SpeechSettings(
            voice = voice(obj["voice"], defaults.voice),
            rate = obj.number("rate")?.coerceIn(SpeechSettings.MIN_RATE, SpeechSettings.MAX_RATE) ?: defaults.rate,
            volume = obj.number("volume")?.coerceIn(0, MAX_VOLUME) ?: defaults.volume,
            templates = templates(obj["templates"]),
            readQuestion = obj.flag("readQuestion") ?: defaults.readQuestion,
            onlyInBackground = obj.flag("onlyInBackground") ?: defaults.onlyInBackground,
            muteWhenMicrophoneActive = obj.flag("muteWhenMicrophoneActive") ?: defaults.muteWhenMicrophoneActive,
        )
    }

    private fun voice(
        element: JsonElement?,
        default: VoiceId?,
    ): VoiceId? =
        when (element) {
            null -> {
                default
            }

            is JsonNull -> {
                null
            }

            else -> {
                val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("bad voice")
                if (text.isBlank()) default else VoiceId(text)
            }
        }

    private fun templates(element: JsonElement?): Map<NotificationKind, String> {
        if (element == null) return emptyMap()
        val obj = element as? JsonObject ?: invalid("templates is not an object")
        return NotificationSettingsSerializer.KINDS
            .mapNotNull { (kind, name) -> obj[name]?.let { kind to templateText(it) } }
            .filter { it.second.isNotBlank() }
            .toMap()
    }

    private fun templateText(element: JsonElement): String =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("a template is not text")
}
