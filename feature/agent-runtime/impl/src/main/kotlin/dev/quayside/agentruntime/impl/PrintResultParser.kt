package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Reads the stdout of `claude -p --output-format json`: the `result` text of the answer. */
internal object PrintResultParser {
    /**
     * `result` of the JSON object in [stdout]. `is_error: true` is [RuntimeError.CommandFailed] with
     * [exitCode] and no text; no JSON object, no string `result` or empty output is
     * [RuntimeError.Unparseable] with a fixed detail. Unknown fields are ignored. Never quotes [stdout].
     */
    fun parse(
        stdout: String,
        exitCode: Int,
    ): Outcome<String, RuntimeError> {
        val root = parseObject(stdout)
        val flag = root?.get("is_error")
        val isError = flag.asBoolean() == true
        val result = (root?.get("result") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            isError -> Outcome.Err(RuntimeError.CommandFailed(exitCode, ""))
            result == null || flag.isMalformedFlag() -> Outcome.Err(RuntimeError.Unparseable(DETAIL))
            else -> Outcome.Ok(result)
        }
    }

    /** `true`/`false` as a JSON boolean; a string such as `"true"` is not a boolean. */
    private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonElement?.isMalformedFlag(): Boolean = this != null && this !is JsonNull && asBoolean() == null

    private fun parseObject(stdout: String): JsonObject? =
        try {
            Json.parseToJsonElement(stdout) as? JsonObject
        } catch (_: SerializationException) {
            null
        }

    private const val DETAIL = "print result"
}
