package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountsState
import dev.quayside.accounts.api.anAccount
import dev.quayside.accounts.api.defaultAccount
import dev.quayside.accounts.api.defaultState
import dev.quayside.core.domain.AccountId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * `data` of `accounts.json` on the real `JsonStorage` (ADR 0035): what is written, and what is read back.
 * Files that are refused are in [AccountsSerializerBrokenTest].
 */
class AccountsSerializerTest : AccountsFsTest() {
    private fun written(): JsonObject =
        Json
            .parseToJsonElement(env.read(env.file))
            .jsonObject
            .getValue("data")
            .jsonObject

    private fun entry(
        id: String,
        vararg more: Pair<String, JsonElement>,
    ): JsonObject =
        buildJsonObject {
            put("id", id)
            more.forEach { (k, v) -> put(k, v) }
        }

    private fun created(slug: String): JsonObject =
        entry(
            slug,
            "label" to JsonPrimitive(slug),
            "configDir" to JsonPrimitive(anAccount(slug, env.home).configDir.toString()),
        )

    private suspend fun save(change: (AccountsState) -> AccountsState) {
        env.open().repo.update(change)
    }

    @Test
    fun `default state writes an empty data object`() =
        runTest {
            save { it }

            written() shouldBe JsonObject(emptyMap())
        }

    @Test
    fun `default account with default flags has no entry`() =
        runTest {
            save { it.copy(accounts = it.accounts + anAccount("praca", env.home)) }

            written() shouldBe buildJsonObject { put("accounts", JsonArray(listOf(created("praca")))) }
        }

    @Test
    fun `default account moved second is written with its position`() =
        runTest {
            save { it.copy(accounts = listOf(anAccount("praca", env.home)) + it.accounts) }

            written()["accounts"] shouldBe JsonArray(listOf(created("praca"), entry("default")))
        }

    @Test
    fun `disabled default account is written without configDir`() =
        runTest {
            save { it.copy(accounts = listOf(defaultAccount(env.home, enabled = false))) }

            written()["accounts"] shouldBe JsonArray(listOf(entry("default", "enabled" to JsonPrimitive(false))))
        }

    @Test
    fun `renamed default account at the first place is written with its label only`() =
        runTest {
            save { it.copy(accounts = listOf(defaultAccount(env.home, label = "Moje"))) }

            written()["accounts"] shouldBe JsonArray(listOf(entry("default", "label" to JsonPrimitive("Moje"))))
        }

    @Test
    fun `default account outside automation is written`() =
        runTest {
            save { it.copy(accounts = listOf(defaultAccount(env.home, inAutomation = false))) }

            written()["accounts"] shouldBe JsonArray(listOf(entry("default", "inAutomation" to JsonPrimitive(false))))
        }

    @Test
    fun `inAutomation true is not written and false is`() =
        runTest {
            val out = anAccount("poza", env.home).copy(inAutomation = false)
            save { it.copy(accounts = it.accounts + anAccount("praca", env.home) + out) }

            val accounts = written()["accounts"] as JsonArray
            accounts[0] shouldBe created("praca")
            accounts[1].jsonObject["inAutomation"] shouldBe JsonPrimitive(false)
            accounts[1].jsonObject.containsKey("enabled") shouldBe false
        }

    @Test
    fun `enabled false is written and true is not`() =
        runTest {
            val off = anAccount("wylaczone", env.home).copy(enabled = false)
            save { it.copy(accounts = it.accounts + anAccount("praca", env.home) + off) }

            val accounts = written()["accounts"] as JsonArray
            accounts[0].jsonObject.containsKey("enabled") shouldBe false
            accounts[1].jsonObject["enabled"] shouldBe JsonPrimitive(false)
        }

    @Test
    fun `cooldown 5 h is not written and 2 h is 120 minutes`() =
        runTest {
            val repo = env.open().repo

            repo.update { it.copy(cooldown = 2.hours) }
            written() shouldBe buildJsonObject { put("cooldownMinutes", 120) }

            repo.update { it.copy(cooldown = 5.hours) }
            written() shouldBe JsonObject(emptyMap())
        }

    @Test
    fun `three accounts in mixed order survive a round trip`() =
        runTest {
            val off = anAccount("poza", env.home).copy(enabled = false, inAutomation = false)
            val state =
                AccountsState(
                    listOf(anAccount("praca", env.home), defaultAccount(env.home, "Moje", enabled = false), off),
                    cooldown = 90.minutes,
                )
            save { state }

            env
                .open()
                .repo.state.value shouldBe state
        }

    @Test
    fun `missing file gives only the default account`() =
        runTest {
            env
                .open()
                .repo.state.value shouldBe defaultState(env.home)
        }

    @Test
    fun `unknown keys are skipped`() =
        runTest {
            val dir = env.home.resolve("praca").toString()
            env.writeFile(
                """{"future": 1, "cooldownMinutes": 120, "accounts": [
                    {"id": "praca", "label": "P", "configDir": "$dir", "extra": true}]}""",
            )

            val state =
                env
                    .open()
                    .repo.state.value

            state.accounts shouldBe
                listOf(defaultAccount(env.home), Account(AccountId("praca"), "P", env.home.resolve("praca")))
            state.cooldown shouldBe 2.hours
        }

    @Test
    fun `default entry without a position stays first and without changes`() =
        runTest {
            env.writeFile("""{"accounts": [{"id": "default", "label": "Moje", "inAutomation": false}]}""")

            env
                .open()
                .repo.state.value shouldBe
                AccountsState(listOf(defaultAccount(env.home, "Moje", inAutomation = false)))
        }

    @Test
    fun `cooldown at both ends of the range is read`() =
        runTest {
            env.writeFile("""{"cooldownMinutes": 60}""")
            env
                .open()
                .repo.state.value.cooldown shouldBe 1.hours

            env.writeFile("""{"cooldownMinutes": 1440}""")
            env
                .open()
                .repo.state.value.cooldown shouldBe 24.hours
        }
}
