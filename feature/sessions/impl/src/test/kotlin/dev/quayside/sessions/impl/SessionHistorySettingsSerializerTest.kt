package dev.quayside.sessions.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * `session-history.json` (ADR 0032) written and read by the real store in a temp directory: only the deviations from
 * the defaults are stored (ADR 0024), a value we do not know makes the file broken, a newer schema is never written.
 */
class SessionHistorySettingsSerializerTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val file get() = dir.resolve("session-history.json")

    private suspend fun open(): SessionHistorySettingsRepo =
        sessionHistorySettingsModule(JsonStorage(dir, io), NoOpLogger)

    private fun data(): JsonObject =
        Json
            .parseToJsonElement(file.readText())
            .jsonObject
            .getValue("data")
            .jsonObject

    @Test
    fun `default settings write no keys`() =
        runTest {
            val repo = open()
            repo.update { it.copy(maxAge = HistoryAge.DAYS_7, maxPerProject = HistoryLimit.SESSIONS_50) }

            repo.update { SessionHistorySettings() }

            data() shouldBe JsonObject(emptyMap())
        }

    @Test
    fun `settings that change nothing write an empty data object`() =
        runTest {
            open().update { it }

            data() shouldBe JsonObject(emptyMap())
        }

    @ParameterizedTest
    @EnumSource(HistoryAge::class)
    fun `every age survives a restart`(age: HistoryAge) =
        runTest {
            open().update { it.copy(maxAge = age) }

            open().settings.value.maxAge shouldBe age
        }

    @ParameterizedTest
    @EnumSource(HistoryLimit::class)
    fun `every limit survives a restart`(limit: HistoryLimit) =
        runTest {
            open().update { it.copy(maxPerProject = limit) }

            open().settings.value.maxPerProject shouldBe limit
        }

    @Test
    fun `only the deviations are stored, ages by name and limits as numbers`() =
        runTest {
            open().update { it.copy(maxAge = HistoryAge.UNLIMITED, maxPerProject = HistoryLimit.SESSIONS_1000) }

            data().keys shouldBe setOf("maxAge", "maxPerProject")
            data().getValue("maxAge").jsonPrimitive.content shouldBe "UNLIMITED"
            data().getValue("maxPerProject").jsonPrimitive.int shouldBe 1000
        }

    @Test
    fun `a stored age leaves the limit at its default and the other way round`() =
        runTest {
            open().update { it.copy(maxAge = HistoryAge.DAYS_90) }

            data().keys shouldBe setOf("maxAge")
            open().settings.value shouldBe SessionHistorySettings(maxAge = HistoryAge.DAYS_90)
        }

    @Test
    fun `hidden ids are written sorted and read back`() =
        runTest {
            val repo = open()
            repo.update { it.copy(hidden = setOf(SessionId("ccc"), SessionId("aaa"), SessionId("bbb"))) }

            data().getValue("hidden") shouldBe JsonArray(listOf("aaa", "bbb", "ccc").map(::JsonPrimitive))
            open().settings.value.hidden shouldBe setOf(SessionId("aaa"), SessionId("bbb"), SessionId("ccc"))
        }

    @Test
    fun `an empty file object gives the defaults`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{}}""")

            open().settings.value shouldBe SessionHistorySettings()
        }

    @Test
    fun `an unknown age makes the file broken and gives the defaults`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"maxAge":"FOREVER"}}""")

            open().settings.value shouldBe SessionHistorySettings()

            dir.resolve("session-history.json.broken").exists() shouldBe true
        }

    @Test
    fun `an unknown limit makes the file broken and gives the defaults`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"maxPerProject":75}}""")

            open().settings.value shouldBe SessionHistorySettings()

            dir.resolve("session-history.json.broken").exists() shouldBe true
        }

    @Test
    fun `hidden of the wrong type makes the file broken`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"hidden":"aaa"}}""")

            open().settings.value shouldBe SessionHistorySettings()

            dir.resolve("session-history.json.broken").exists() shouldBe true
        }

    @Test
    fun `a newer schema is NEWER_SCHEMA and the file is never written`() =
        runTest {
            val newer = """{"schemaVersion":2,"data":{"maxAge":"DAYS_7"}}"""
            file.writeText(newer)
            val repo = open()

            repo.update { it.copy(maxAge = HistoryAge.DAYS_90) } shouldBe Outcome.Err(HistorySettingsError.NEWER_SCHEMA)

            file.readText() shouldBe newer
            repo.settings.value shouldBe SessionHistorySettings()
        }

    @Test
    fun `a settings directory that cannot be written gives WRITE_FAILED and publishes nothing`() =
        runTest {
            val blocked = dir.resolve("blocked").also { it.writeText("a file, not a directory") }
            val repo = sessionHistorySettingsModule(JsonStorage(blocked, io), NoOpLogger)

            repo.update { it.copy(maxAge = HistoryAge.DAYS_7) } shouldBe Outcome.Err(HistorySettingsError.WRITE_FAILED)

            repo.settings.value shouldBe SessionHistorySettings()
        }
}
