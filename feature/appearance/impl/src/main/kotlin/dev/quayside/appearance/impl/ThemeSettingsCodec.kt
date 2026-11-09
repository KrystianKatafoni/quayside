package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.designtokens.TypographyId
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

private const val SCHEME = "scheme"
private const val TYPOGRAPHY = "typography"
private const val UI_SCALE = "uiScale"
private const val DENSITY = "density"
private const val FOLLOW_SYSTEM = "followSystem"
private const val DAY_SCHEME = "dayScheme"
private const val NIGHT_SCHEME = "nightScheme"

/** [settings] read from a theme.json `data` object and the JSON keys whose value was present but invalid. */
internal data class DecodedSettings(
    val settings: AppearanceSettings,
    val invalid: Set<String>,
)

/**
 * The fields of [obj] (flat, docs/08 and ADR 0023): a missing key gives the default and is not invalid; a wrong type,
 * an unknown enum name or an id outside [catalog] gives the default and puts the key into `invalid`. Never throws.
 */
internal fun decodeSettings(
    obj: JsonObject,
    catalog: ThemeCatalog,
): DecodedSettings {
    val reader = FieldReader(obj, catalog)
    val defaults = AppearanceSettings()
    val settings =
        AppearanceSettings(
            scheme = reader.scheme(SCHEME, defaults.scheme),
            typography = reader.typography(defaults.typography),
            uiScale = reader.enum(UI_SCALE, defaults.uiScale),
            density = reader.enum(DENSITY, defaults.density),
            dayNight =
                DayNight(
                    enabled = reader.field(FOLLOW_SYSTEM, defaults.dayNight.enabled) { it.flag() },
                    day = reader.scheme(DAY_SCHEME, defaults.dayNight.day),
                    night = reader.scheme(NIGHT_SCHEME, defaults.dayNight.night),
                ),
        )
    return DecodedSettings(settings, reader.invalid)
}

/**
 * The flat `data` object of theme.json for [settings]: every field, also the ones with default values; the
 * `customSchemes` array only when there are any (ADR 0024).
 */
internal fun encodeSettings(settings: AppearanceSettings): JsonObject =
    buildJsonObject {
        put(SCHEME, JsonPrimitive(settings.scheme.value))
        put(TYPOGRAPHY, JsonPrimitive(settings.typography.value))
        put(UI_SCALE, JsonPrimitive(settings.uiScale.name))
        put(DENSITY, JsonPrimitive(settings.density.name))
        put(FOLLOW_SYSTEM, JsonPrimitive(settings.dayNight.enabled))
        put(DAY_SCHEME, JsonPrimitive(settings.dayNight.day.value))
        put(NIGHT_SCHEME, JsonPrimitive(settings.dayNight.night.value))
        if (settings.customSchemes.isNotEmpty()) put(CUSTOM_SCHEMES, encodeSchemes(settings.customSchemes))
    }

/** Reads the fields of [obj]; every key that is present but not parsable is collected in [invalid]. */
private class FieldReader(
    private val obj: JsonObject,
    private val catalog: ThemeCatalog,
) {
    val invalid = mutableSetOf<String>()

    /** The value of [key] parsed by [parse]; the [default] when the key is missing or not parsable. */
    fun <T : Any> field(
        key: String,
        default: T,
        parse: (JsonPrimitive) -> T?,
    ): T {
        val element = obj[key] ?: return default
        val parsed = (element as? JsonPrimitive)?.let(parse)
        if (parsed == null) invalid += key
        return parsed ?: default
    }

    // Ids come from the catalog, never from a constructor on raw text (SchemeId and TypographyId would throw).
    fun scheme(
        key: String,
        default: SchemeId,
    ): SchemeId = field(key, default) { p -> catalog.themes.firstOrNull { it.id.value == p.text() }?.id }

    fun typography(default: TypographyId): TypographyId =
        field(TYPOGRAPHY, default) { p -> catalog.typographies.firstOrNull { it.id.value == p.text() }?.id }

    inline fun <reified E : Enum<E>> enum(
        key: String,
        default: E,
    ): E = field(key, default) { p -> enumValues<E>().firstOrNull { it.name == p.text() } }
}

private fun JsonPrimitive.text(): String? = if (isString) content else null

private fun JsonPrimitive.flag(): Boolean? = if (isString) null else booleanOrNull

/**
 * `data` of theme.json for the store: reads like [decodeSettings] (invalid fields fall back, unknown keys are
 * ignored) against [catalog] extended with the accepted `customSchemes` of the file (valid, free id, NFR-6; the
 * others are dropped and `customSchemes` is reported as invalid); anything that is not a JSON object throws
 * `SerializationException`, so the file is moved aside. [onInvalid] gets the JSON keys (never values) of fields that
 * were present but invalid, so the owner can log a fixed category.
 */
internal class ThemeSettingsSerializer(
    private val catalog: ThemeCatalog,
    private val onInvalid: (Set<String>) -> Unit = {},
) : KSerializer<AppearanceSettings> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AppearanceSettings,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("theme.json needs a JSON encoder")
        json.encodeJsonElement(encodeSettings(value))
    }

    override fun deserialize(decoder: Decoder): AppearanceSettings {
        val json = decoder as? JsonDecoder ?: throw SerializationException("theme.json needs a JSON decoder")
        val element = json.decodeJsonElement()
        if (element !is JsonObject) throw SerializationException("theme.json data is not an object")
        val schemes = decodeSchemes(element)
        val accepted = acceptedSchemes(catalog, schemes.schemes)
        val decoded = decodeSettings(element, catalogWith(catalog, accepted))
        val dropped = schemes.invalid || accepted.size != schemes.schemes.size
        val invalid = if (dropped) decoded.invalid + CUSTOM_SCHEMES else decoded.invalid
        if (invalid.isNotEmpty()) onInvalid(invalid)
        return decoded.settings.copy(customSchemes = accepted)
    }
}
