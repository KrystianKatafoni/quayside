package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import io.kotest.assertions.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal const val SESSION_ID = "00000000-0000-4000-8000-000000000001"
internal const val FIRST_TIME = "2026-01-01T10:00:00Z"
internal const val SECOND_TIME = "2026-01-01T10:05:00Z"
internal const val LAST_TIME = "2026-01-01T11:30:00Z"

/**
 * One line of a session file: a `user` or `assistant` entry in the shape of Claude Code's transcripts.
 * A null argument leaves the field out.
 */
internal fun sessionEntry(
    type: String = "user",
    sessionId: String? = SESSION_ID,
    cwd: String? = "/p",
    timestamp: String? = FIRST_TIME,
    content: JsonElement? = JsonPrimitive("hello"),
): String =
    buildJsonObject {
        put("type", type)
        sessionId?.let { put("sessionId", it) }
        cwd?.let { put("cwd", it) }
        timestamp?.let { put("timestamp", it) }
        content?.let {
            put(
                "message",
                buildJsonObject {
                    put("role", type)
                    put("content", it)
                },
            )
        }
    }.toString()

/** [entry] (one line made by [sessionEntry]) with the boolean field [name] set to [value]. */
internal fun withFlag(
    entry: String,
    name: String,
    value: Boolean,
): String {
    val fields = checkNotNull(Json.parseToJsonElement(entry) as? JsonObject)
    return JsonObject(fields + (name to JsonPrimitive(value))).toString()
}

/** A `content` array of parts: `text` for a string, `tool_result` for the marker [TOOL_RESULT]. */
internal fun parts(vararg parts: String): JsonElement =
    JsonArray(
        parts.map { part ->
            buildJsonObject {
                if (part == TOOL_RESULT) {
                    put("type", "tool_result")
                    put("content", "tool output")
                } else {
                    put("type", "text")
                    put("text", part)
                }
            }
        },
    )

internal const val TOOL_RESULT = "<tool_result>"

internal fun lines(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

internal fun parseWhole(text: String): Outcome<SessionFileSummary, SessionFileProblem> =
    SessionFiles.parse(SESSION_ID, head = text, tail = "", headIsWhole = true)

internal fun summaryOf(outcome: Outcome<SessionFileSummary, SessionFileProblem>): SessionFileSummary =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> fail("expected Ok, got ${outcome.error}")
    }

internal fun problemOf(outcome: Outcome<SessionFileSummary, SessionFileProblem>): SessionFileProblem =
    when (outcome) {
        is Outcome.Ok -> fail("expected Err, got Ok")
        is Outcome.Err -> outcome.error
    }

internal fun at(time: String): Instant = Instant.parse(time)
