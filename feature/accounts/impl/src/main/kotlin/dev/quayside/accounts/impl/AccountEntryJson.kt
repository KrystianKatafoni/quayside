package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountRules
import dev.quayside.core.domain.AccountId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val ID = "id"
private const val LABEL = "label"
private const val CONFIG_DIR = "configDir"
private const val ENABLED = "enabled"
private const val IN_AUTOMATION = "inAutomation"

/** Rejects the file with a fixed [reason] that never quotes the input (ENG-C8). */
internal fun invalid(reason: String): Nothing = throw SerializationException(reason)

/** The integer at [key]; null when the key is missing, invalid when it is not an integer. */
internal fun JsonObject.number(key: String): Int? =
    get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull ?: invalid("bad $key") }

private fun JsonObject.flag(key: String): Boolean? =
    get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull ?: invalid("bad $key") }

private fun JsonObject.text(key: String): String? =
    get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: invalid("bad $key") }

/** The entry of [account] at [position] of the list, or null when it is the default one and has no deviation. */
internal fun encodeEntry(
    account: Account,
    position: Int,
): JsonObject? {
    val plain = account.isDefault && position == 0 && account.label == null && account.enabled && account.inAutomation
    if (plain) return null
    return buildJsonObject {
        put(ID, account.id.value)
        account.label?.let { put(LABEL, it) }
        if (!account.isDefault) put(CONFIG_DIR, account.configDir.toString())
        if (!account.enabled) put(ENABLED, false)
        if (!account.inAutomation) put(IN_AUTOMATION, false)
    }
}

/** One account of the file; the default one has `home/.claude` as its directory (ADR 0035 p.2). */
internal fun decodeEntry(
    element: JsonElement,
    home: Path,
): Account {
    val entry = element as? JsonObject ?: invalid("account is not an object")
    val id = entry.text(ID)?.takeIf { AccountRules.isValidId(it) } ?: invalid("bad id")
    val label = entry.text(LABEL)
    val enabled = entry.flag(ENABLED) ?: true
    val inAutomation = entry.flag(IN_AUTOMATION) ?: true
    if (id == AccountId.DEFAULT.value) {
        if (entry.containsKey(CONFIG_DIR)) invalid("default account has a directory")
        return defaultAccountOf(home, label, enabled, inAutomation)
    }
    val dir = entry.text(CONFIG_DIR) ?: invalid("account without a directory")
    return Account(AccountId(id), label, toPath(dir), enabled, inAutomation)
}

private fun toPath(text: String): Path =
    try {
        Path.of(text)
    } catch (_: InvalidPathException) {
        invalid("bad $CONFIG_DIR")
    }
