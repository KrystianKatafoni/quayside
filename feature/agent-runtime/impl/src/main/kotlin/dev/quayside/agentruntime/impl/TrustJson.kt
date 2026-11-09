package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.core.domain.Outcome
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * Pure merge of `hasTrustDialogAccepted` into the text of Claude Code's `.claude.json` (ADR 0031, point 5).
 * The tree is parsed and written back whole: key order, unknown fields and number literals stay.
 */
internal object TrustJson {
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /**
     * Text with trust for [key]; `Ok(null)` when [key] is already trusted; `CONFIG_INVALID` for a wrong shape.
     * Exception messages of the parser quote the input, so they are never kept.
     */
    fun withTrust(
        text: String,
        key: String,
    ): Outcome<String?, TrustError> {
        val root = (parse(text) as? JsonObject)?.takeIf { it.literalsValid() }
        val projects = root?.child(PROJECTS)
        val entry = projects?.child(key)
        val flag = entry?.get(FLAG)
        return when {
            root == null || projects == null || entry == null -> {
                invalid()
            }

            flag != null && !flag.isBoolean() -> {
                invalid()
            }

            flag?.isTrue() == true -> {
                Outcome.Ok(null)
            }

            else -> {
                val newProjects = JsonObject(projects + (key to JsonObject(entry + (FLAG to JsonPrimitive(true)))))
                val rendered =
                    pretty.encodeToString(
                        JsonElement.serializer(),
                        verbatim(JsonObject(root + (PROJECTS to newProjects))),
                    )
                // A lone surrogate cannot be written as UTF-8 (it would become `?`).
                if (Charsets.UTF_8.newEncoder().canEncode(rendered)) {
                    Outcome.Ok(if (text.endsWith("\n")) rendered + "\n" else rendered)
                } else {
                    invalid()
                }
            }
        }
    }

    /** The child object, an empty one when missing, null when it is something else. */
    private fun JsonObject.child(name: String): JsonObject? =
        when (val value = this[name]) {
            null -> JsonObject(emptyMap())
            is JsonObject -> value
            else -> null
        }

    private fun parse(text: String): JsonElement? =
        try {
            pretty.parseToJsonElement(text)
        } catch (ignored: IllegalArgumentException) {
            null // SerializationException is an IllegalArgumentException
        }

    // The default encoder writes numbers through Long/Double (`1e3` becomes `1000.0`); unquoted literals are verbatim.
    @OptIn(ExperimentalSerializationApi::class)
    private fun verbatim(element: JsonElement): JsonElement =
        when {
            element is JsonObject -> {
                JsonObject(element.mapValues { verbatim(it.value) })
            }

            element is JsonArray -> {
                JsonArray(element.map(::verbatim))
            }

            element is JsonPrimitive && !element.isString && element !is JsonNull -> {
                JsonUnquotedLiteral(
                    element.content,
                )
            }

            else -> {
                element
            }
        }

    /**
     * Unquoted values must be `true`, `false`, `null` or a JSON number: the parser is not strict about other words
     * (`NaN`, `abc`, `01`), and [verbatim] would write them back as they are.
     */
    private fun JsonElement.literalsValid(): Boolean =
        when (this) {
            is JsonObject -> values.all { it.literalsValid() }
            is JsonArray -> all { it.literalsValid() }
            is JsonNull -> true
            is JsonPrimitive -> isString || content == "true" || content == "false" || NUMBER.matches(content)
        }

    private fun invalid(): Outcome<String?, TrustError> = Outcome.Err(TrustError.CONFIG_INVALID)

    private fun JsonElement.isBoolean(): Boolean =
        this is JsonPrimitive && !isString && (content == "true" || content == "false")

    private fun JsonElement.isTrue(): Boolean = this is JsonPrimitive && !isString && content == "true"

    private val NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")
    private const val PROJECTS = "projects"
    private const val FLAG = "hasTrustDialogAccepted"
}
