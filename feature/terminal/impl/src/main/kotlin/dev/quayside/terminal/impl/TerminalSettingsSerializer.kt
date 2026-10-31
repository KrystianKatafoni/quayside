package dev.quayside.terminal.impl

import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRules
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * `data` of `terminal.json` (ADR 0016): flat fields; a missing key, wrong type, unknown enum name or value
 * outside `TerminalSettingsRules` gives the default of that field; unknown keys are ignored; anything that is
 * not a JSON object throws `SerializationException` (the file is moved aside).
 */
internal object TerminalSettingsSerializer : KSerializer<TerminalSettings> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: TerminalSettings,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("JSON encoder required")
        json.encodeJsonElement(
            buildJsonObject {
                put("shell", value.shell.name)
                put("startDirectory", value.startDirectory.name)
                put("fontFamily", value.fontFamily)
                put("fontSize", value.fontSize)
                put("scrollbackLines", value.scrollbackLines)
                put("ligatures", value.ligatures)
                put("optionAsMeta", value.optionAsMeta)
            },
        )
    }

    override fun deserialize(decoder: Decoder): TerminalSettings {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON decoder required")
        val obj = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("object expected")
        val d = TerminalSettings()
        return TerminalSettings(
            shell = obj.enum("shell", d.shell),
            startDirectory = obj.enum("startDirectory", d.startDirectory),
            fontFamily = obj.family() ?: d.fontFamily,
            fontSize = obj.int("fontSize", TerminalSettingsRules.FONT_SIZES, d.fontSize),
            scrollbackLines = obj.int("scrollbackLines", TerminalSettingsRules.SCROLLBACK_LINES, d.scrollbackLines),
            ligatures = obj.flag("ligatures", d.ligatures),
            optionAsMeta = obj.flag("optionAsMeta", d.optionAsMeta),
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    private inline fun <reified E : Enum<E>> JsonObject.enum(
        key: String,
        default: E,
    ): E = text(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun JsonObject.family(): String? =
        text("fontFamily")?.trim()?.takeIf {
            it.isNotEmpty() &&
                it.length <= TerminalSettingsRules.MAX_FONT_FAMILY_LENGTH
        }

    private fun JsonObject.int(
        key: String,
        range: IntRange,
        default: Int,
    ): Int = number(key)?.takeIf { it in range } ?: default

    private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.flag(
        key: String,
        default: Boolean,
    ): Boolean = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: default
}
