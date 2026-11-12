package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountRules
import dev.quayside.accounts.api.AccountsState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * `data` of `accounts.json` (ADR 0035): `{"accounts": [{"id", "label", "configDir", …}], "cooldownMinutes": 120}`.
 * Only deviations from the defaults are written; the default account has an entry only when it deviates and never
 * a `configDir` (it is `home/.claude`). Unknown keys are skipped; a wrong type or a broken invariant throws
 * [kotlinx.serialization.SerializationException] with a fixed category, never quoting the input (ENG-C8).
 */
internal class AccountsSerializer(
    private val home: Path,
) : KSerializer<AccountsState> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AccountsState,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        val entries = value.accounts.mapIndexedNotNull { index, account -> encodeEntry(account, index) }
        json.encodeJsonElement(
            buildJsonObject {
                if (entries.isNotEmpty()) put(ACCOUNTS, JsonArray(entries))
                if (value.cooldown != AccountRules.DEFAULT_COOLDOWN) {
                    put(COOLDOWN_MINUTES, value.cooldown.inWholeMinutes)
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): AccountsState {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val root = json.decodeJsonElement() as? JsonObject ?: invalid("data is not an object")
        val array = root[ACCOUNTS] ?: JsonArray(emptyList())
        val listed = (array as? JsonArray ?: invalid("accounts is not an array")).map { decodeEntry(it, home) }
        val accounts = if (listed.any { it.isDefault }) listed else listOf(defaultAccountOf(home)) + listed
        val cooldown = root.number(COOLDOWN_MINUTES)?.minutes ?: AccountRules.DEFAULT_COOLDOWN
        val state = AccountsState(accounts, cooldown)
        AccountRules.validate(state, home)?.let { invalid("broken invariant ${it.name}") }
        return state
    }

    private companion object {
        const val ACCOUNTS = "accounts"
        const val COOLDOWN_MINUTES = "cooldownMinutes"
    }
}
