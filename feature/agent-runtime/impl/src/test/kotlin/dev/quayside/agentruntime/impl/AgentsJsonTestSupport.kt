package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.A_PROJECT_DIR
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import io.kotest.assertions.fail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

// Shared helpers for the parser tests: a valid background entry with overridable fields.

/** "Now" passed to the parser; deliberately different from every `startedAt` in the tests. */
internal val OBSERVED_AT: Instant = Instant.parse("2026-10-18T12:00:00Z")

internal const val ID = "7c5dcf5d"
internal const val CONVERSATION = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"
internal const val STARTED_AT_MILLIS = 1_792_328_042_940L

/** Marker for [entry] overrides: the field is removed from the object. */
internal data object Absent

/**
 * A background session entry as `claude agents --json` would print it. Each override replaces one
 * field: a [String], [Number], [Boolean], `null` (JSON null), [JsonElement] or [Absent] (no field).
 */
internal fun entry(vararg overrides: Pair<String, Any?>): JsonObject {
    val fields =
        linkedMapOf<String, Any?>(
            "id" to ID,
            "kind" to "background",
            "sessionId" to CONVERSATION,
            "name" to "fix-login",
            "cwd" to A_PROJECT_DIR.toString(),
            "state" to "working",
            "startedAt" to STARTED_AT_MILLIS,
            "pid" to 4242,
        )
    overrides.forEach { (key, value) -> if (value === Absent) fields.remove(key) else fields[key] = value }
    return JsonObject(fields.mapValues { (_, value) -> json(value) })
}

internal fun json(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> error("unsupported test value $value")
    }

internal fun document(vararg elements: JsonElement): String = JsonArray(elements.toList()).toString()

internal fun parseOk(stdout: String): AgentsListing =
    when (val outcome = AgentsJsonParser.parse(stdout, OBSERVED_AT)) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> fail("expected Ok, got $outcome")
    }

internal fun parseErr(stdout: String): RuntimeError.Unparseable =
    when (val outcome = AgentsJsonParser.parse(stdout, OBSERVED_AT)) {
        is Outcome.Ok -> fail("expected Err, got Ok with ${outcome.value.sessions.size} sessions")
        is Outcome.Err -> outcome.error
    }

/** Parses a single entry that must be kept and returns the session plus the notices about it. */
internal fun parseSingle(entry: JsonObject): AgentsListing {
    val listing = parseOk(document(entry))
    if (listing.sessions.size != 1) fail("expected exactly one session, got $listing")
    return listing
}

internal val ALL_UNPARSEABLE_REASONS: Set<String> =
    setOf(
        UnparseableReason.EMPTY,
        UnparseableReason.TOO_LARGE,
        UnparseableReason.MALFORMED,
        UnparseableReason.NOT_AN_ARRAY,
    )
