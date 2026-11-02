package dev.quayside.notifications.impl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** The boolean at [key]; null when the key is missing, [invalid] when it is not a boolean. */
internal fun JsonObject.flag(key: String): Boolean? =
    get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull ?: invalid("bad $key") }

/** The integer at [key]; null when the key is missing, [invalid] when it is not an integer. */
internal fun JsonObject.number(key: String): Int? =
    get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull ?: invalid("bad $key") }

/** Rejects the file with a fixed [reason] that never quotes the input (ENG-C8). */
internal fun invalid(reason: String): Nothing = throw SerializationException(reason)
