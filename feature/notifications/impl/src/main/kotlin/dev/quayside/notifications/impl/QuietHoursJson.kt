package dev.quayside.notifications.impl

import dev.quayside.notifications.api.QuietHours
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalTime

/**
 * The `quietHours` section of `notifications.json`: `{"enabled", "from": "22:00", "to": "07:00", "respectFocus"}`.
 * A missing key is the default; a time that is not `HH:MM` within 00:00..23:59 is the default; a wrong type throws.
 */
internal object QuietHoursJson {
    private val TIME = Regex("""([01]\d|2[0-3]):([0-5]\d)""")

    fun encode(quiet: QuietHours): JsonObject =
        buildJsonObject {
            put("enabled", quiet.enabled)
            put("from", quiet.from.toString())
            put("to", quiet.to.toString())
            put("respectFocus", quiet.respectFocus)
        }

    fun decode(element: JsonElement?): QuietHours {
        val defaults = QuietHours()
        if (element == null) return defaults
        val obj = element as? JsonObject ?: invalid("quietHours is not an object")
        return QuietHours(
            enabled = obj.flag("enabled") ?: defaults.enabled,
            from = time(obj["from"], defaults.from),
            to = time(obj["to"], defaults.to),
            respectFocus = obj.flag("respectFocus") ?: defaults.respectFocus,
        )
    }

    private fun time(
        element: JsonElement?,
        default: LocalTime,
    ): LocalTime {
        if (element == null) return default
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("bad time")
        return TIME.matchEntire(text)?.destructured?.let { (hour, minute) ->
            LocalTime.of(hour.toInt(), minute.toInt())
        } ?: default
    }
}
