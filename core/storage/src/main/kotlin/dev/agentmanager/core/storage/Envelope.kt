package dev.agentmanager.core.storage

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.catching
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.getOrElse
import dev.agentmanager.core.domain.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer

/**
 * Pure (de)serialization of the file layout `{"schemaVersion": N, "data": <value>}`.
 *
 * Broken reasons are fixed categories: parser messages quote the file, and file contents (project
 * paths, user names) must never reach logs (ENG-C8).
 */
internal object Envelope {
    private const val SCHEMA_VERSION = "schemaVersion"
    private const val DATA = "data"

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun <T> encode(
        value: T,
        spec: StoreSpec<T>,
    ): ByteArray =
        json
            .encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put(SCHEMA_VERSION, JsonPrimitive(spec.schemaVersion))
                    put(DATA, json.encodeToJsonElement(spec.serializer, value))
                },
            ).toByteArray(Charsets.UTF_8)

    fun <T> decode(
        bytes: ByteArray,
        spec: StoreSpec<T>,
    ): Decoded<T> =
        step("invalid UTF-8") {
            Charsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.flatMap { text -> step("invalid JSON") { json.parseToJsonElement(text) } }
            .flatMap { root -> if (root is JsonObject) versioned(root, spec) else Outcome.Err("not a JSON object") }
            .getOrElse { Decoded.Broken(it) }

    /** A newer version is reported before anything else: a future envelope must never be moved aside. */
    private fun <T> versioned(
        root: JsonObject,
        spec: StoreSpec<T>,
    ): Outcome<Decoded<T>, String> {
        val version = (root[SCHEMA_VERSION] as? JsonPrimitive)?.intOrNull
        val data = root[DATA]
        return when {
            version == null -> {
                Outcome.Err("missing $SCHEMA_VERSION")
            }

            version > spec.schemaVersion -> {
                Outcome.Ok(Decoded.Newer(version))
            }

            version < 1 -> {
                Outcome.Err("invalid $SCHEMA_VERSION")
            }

            data == null -> {
                Outcome.Err("missing $DATA")
            }

            version == spec.schemaVersion -> {
                typed(data, spec).map { Decoded.Current(it) }
            }

            else -> {
                step("migration from v$version failed") { migrate(data, version, spec) }
                    .flatMap { typed(it, spec) }
                    .map { Decoded.Migrated(it) }
            }
        }
    }

    private fun <T> typed(
        data: JsonElement,
        spec: StoreSpec<T>,
    ): Outcome<T, String> = step("data does not match type") { json.decodeFromJsonElement(spec.serializer, data) }

    /** Migrations work on objects; data of another shape at an old version fails the migration. */
    private fun migrate(
        data: JsonElement,
        from: Int,
        spec: StoreSpec<*>,
    ): JsonElement =
        spec.migrations
            .sortedBy { it.fromVersion }
            .filter { it.fromVersion >= from }
            .fold(data) { current, migration -> migration.migrate(current.jsonObject) }

    private inline fun <R> step(
        reason: String,
        block: () -> R,
    ): Outcome<R, String> = catching({ reason }, block)
}

/** What a file on disk turned out to contain. */
internal sealed interface Decoded<out T> {
    data class Current<T>(
        val value: T,
    ) : Decoded<T>

    data class Migrated<T>(
        val value: T,
    ) : Decoded<T>

    data class Newer(
        val version: Int,
    ) : Decoded<Nothing>

    data class Broken(
        val reason: String,
    ) : Decoded<Nothing>
}
