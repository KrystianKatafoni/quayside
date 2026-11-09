package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundSettings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The `sound` section of `notifications.json`, written only when it differs from the default and then only the
 * deviations (ADR 0024). A missing key is the default; `volume` is clamped; a sound name that is not a valid
 * [SoundId] is skipped (it would build a path); a wrong type throws.
 */
internal object SoundSettingsJson {
    fun encode(sound: SoundSettings): JsonObject =
        buildJsonObject {
            if (sound.volume != SoundSettings.DEFAULT_VOLUME) put("volume", sound.volume)
            val chosen = sound.sounds.filter { (kind, id) -> id != SoundSettings.defaultSound(kind) }
            if (chosen.isNotEmpty()) {
                putJsonObject("sounds") {
                    chosen.forEach { (kind, id) -> put(NotificationSettingsSerializer.KINDS.getValue(kind), id.name) }
                }
            }
        }

    fun decode(element: JsonElement?): SoundSettings {
        if (element == null) return SoundSettings()
        val obj = element as? JsonObject ?: invalid("sound is not an object")
        return SoundSettings(
            volume =
                obj.number("volume")?.coerceIn(SoundSettings.MIN_VOLUME, SoundSettings.MAX_VOLUME)
                    ?: SoundSettings.DEFAULT_VOLUME,
            sounds = sounds(obj["sounds"]),
        )
    }

    private fun sounds(element: JsonElement?): Map<NotificationKind, SoundId> {
        if (element == null) return emptyMap()
        val obj = element as? JsonObject ?: invalid("sounds is not an object")
        return NotificationSettingsSerializer.KINDS
            .mapNotNull { (kind, name) -> obj[name]?.let { value -> soundName(value)?.let { kind to it } } }
            .toMap()
    }

    private fun soundName(element: JsonElement): SoundId? =
        SoundId.parse((element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("a sound is not text"))
}
