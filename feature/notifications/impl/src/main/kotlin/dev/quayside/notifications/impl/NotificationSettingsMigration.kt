package dev.quayside.notifications.impl

import dev.quayside.core.storage.Migration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Current schema of `notifications.json`. Version 2 (AM-812) moved the default of a finished turn to popup + sound. */
internal const val SETTINGS_SCHEMA_VERSION = 2

/** The migrations of `notifications.json` from schema 1 up to [SETTINGS_SCHEMA_VERSION]. */
internal val SETTINGS_MIGRATIONS: List<Migration> = listOf(Migration(1, ::dropOldTurnEndedDefault))

/**
 * Schema 1 to 2. The old writer saved the channels of every kind, so each saved file holds `"turnEnded": ["popup"]`,
 * the default of that time. That value (as a set, so a repeat does not matter) is dropped: the key missing means the
 * new default. Other values, also `[]` and `["popup", "voice"]`, are conscious choices and stay. Limit: a
 * popup-only choice made before the migration cannot be told from the old default and is lost; the user can switch
 * the sound off again. Data of an unexpected shape is returned as it is; the serializer then rejects it.
 */
internal fun dropOldTurnEndedDefault(data: JsonObject): JsonObject {
    val channels = data[CHANNELS] as? JsonObject
    val turnEnded = channels?.get(TURN_ENDED) as? JsonArray
    val names = turnEnded?.mapTo(mutableSetOf()) { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    return if (channels != null && names == OLD) {
        JsonObject(data + (CHANNELS to JsonObject(channels - TURN_ENDED)))
    } else {
        data
    }
}

private const val CHANNELS = "channels"
private const val TURN_ENDED = "turnEnded"
private val OLD: Set<String?> = setOf("popup")
