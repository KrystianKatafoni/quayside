package dev.quayside.appearance.impl

import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeColors
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.core.designtokens.Rgba
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

internal const val CUSTOM_SCHEMES = "customSchemes"
private const val KEY = "key"
private const val NAME = "name"
private const val MODE = "mode"
private const val RADIUS = "radius"
private const val COLORS = "colors"
private val KEY_FORMAT = Regex("[a-z0-9-]+")
private val RADII = 0..CustomScheme.MAX_RADIUS
private val COLUMNS: List<String> = listOf("bg", "panel", "text", "accent", "warn", "ok", "err", "work")

/** The custom schemes of a theme file: the valid ones in file order and whether anything was present but invalid. */
internal data class DecodedSchemes(
    val schemes: List<CustomScheme>,
    val invalid: Boolean,
)

/** The JSON object of [scheme]: key, name, mode, radius and the eight `#RRGGBB` colours. */
internal fun encodeScheme(scheme: CustomScheme): JsonObject =
    buildJsonObject {
        put(KEY, JsonPrimitive(scheme.id.value))
        put(NAME, JsonPrimitive(scheme.name))
        put(MODE, JsonPrimitive(scheme.mode.name))
        put(RADIUS, JsonPrimitive(scheme.radius))
        put(
            COLORS,
            buildJsonObject {
                COLUMNS.zip(scheme.colors.all()).forEach { (column, color) -> put(column, JsonPrimitive(color.hex())) }
            },
        )
    }

/** The `customSchemes` array of [schemes]. */
internal fun encodeSchemes(schemes: List<CustomScheme>): JsonArray = JsonArray(schemes.map(::encodeScheme))

/**
 * The scheme in [element], or null when anything is missing or wrong: not an object, a key outside `[a-z0-9-]+`, a
 * name of 1..40 characters, an unknown mode, a radius outside 0..24, a colour that is not `#RRGGBB`. Never throws;
 * unknown keys are ignored.
 */
internal fun parseScheme(element: JsonElement): CustomScheme? {
    val obj = element as? JsonObject ?: return null
    val key = obj.text(KEY)?.takeIf(KEY_FORMAT::matches)
    val name = obj.text(NAME)?.takeIf { it.isNotBlank() && it.length <= CustomScheme.MAX_NAME }
    val mode = ThemeMode.entries.firstOrNull { it.name == obj.text(MODE) }
    val radius = (obj[RADIUS] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in RADII }
    val colors = (obj[COLORS] as? JsonObject)?.let(::parseColors)
    val head = if (key != null && name != null) SchemeId(key) to name else null
    val tail = if (mode != null && radius != null && colors != null) Triple(mode, radius, colors) else null
    return if (head != null && tail != null) {
        CustomScheme(head.first, head.second, tail.first, tail.third, tail.second)
    } else {
        null
    }
}

private fun parseColors(obj: JsonObject): SchemeColors? {
    val parsed = COLUMNS.map { column -> obj.text(column)?.let(Rgba::parse) }
    if (parsed.any { it == null }) return null
    val by = COLUMNS.zip(parsed.filterNotNull()).toMap()
    return SchemeColors(
        bg = by.getValue("bg"),
        panel = by.getValue("panel"),
        text = by.getValue("text"),
        accent = by.getValue("accent"),
        warn = by.getValue("warn"),
        ok = by.getValue("ok"),
        err = by.getValue("err"),
        work = by.getValue("work"),
    )
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * The `customSchemes` of the data object [data]: a missing key gives no schemes; a value that is not an array, or an
 * element that [parseScheme] refuses, sets `invalid` and is skipped. Never throws.
 */
internal fun decodeSchemes(data: JsonObject): DecodedSchemes =
    when (val element = data[CUSTOM_SCHEMES]) {
        null -> {
            DecodedSchemes(emptyList(), invalid = false)
        }

        is JsonArray -> {
            element.map(::parseScheme).let { parsed ->
                DecodedSchemes(
                    parsed.filterNotNull(),
                    parsed.any {
                        it ==
                            null
                    },
                )
            }
        }

        else -> {
            DecodedSchemes(emptyList(), invalid = true)
        }
    }
