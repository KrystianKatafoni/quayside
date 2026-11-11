package dev.quayside.sessions.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.StoredPlace
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The `places` key of `session-history.json` (AM-314, ADR 0033) written and read by the real store, as the lessons
 * demand: the behaviour behind the serializer is tested on `JsonStorage`, not on an in-memory store. Only the
 * deviations are stored; a bad entry makes the whole file broken (the `.broken` file is the proof).
 */
class SessionPlacesSerializerTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val file get() = dir.resolve("session-history.json")
    private val uuid = SessionId("0b6f0000-0000-4000-8000-000000000000")
    private val short = SessionId("7c5dcf5d")
    private val soft = StoredPlace(ProjectId("p1"), PlaceAssignment.Subfolder("soft"))
    private val root = StoredPlace(ProjectId("p1"), PlaceAssignment.ProjectRoot)

    private suspend fun open(): SessionHistorySettingsRepo =
        sessionHistorySettingsModule(JsonStorage(dir, io), NoOpLogger)

    private fun data(): JsonObject =
        Json
            .parseToJsonElement(file.readText())
            .jsonObject
            .getValue("data")
            .jsonObject

    private fun places(): JsonObject = data().getValue("places").jsonObject

    private fun write(places: String) = file.writeText("""{"schemaVersion":1,"data":{"places":$places}}""")

    private fun isBroken(): Boolean = dir.resolve("session-history.json.broken").exists()

    @Test
    fun `default settings write no places key`() =
        runTest {
            val repo = open()
            repo.update { it.copy(places = mapOf(uuid to soft)) }

            repo.update { it.copy(places = emptyMap()) }

            data().keys shouldBe emptySet()
            repo.update { it }
            data().keys shouldBe emptySet()
        }

    @Test
    fun `root place writes no subfolder key`() =
        runTest {
            open().update { it.copy(places = mapOf(short to root)) }

            places().getValue(short.value).jsonObject.keys shouldBe setOf("project")
            places()
                .getValue(short.value)
                .jsonObject
                .getValue("project")
                .jsonPrimitive.content shouldBe "p1"
        }

    @Test
    fun `a subfolder place writes the project and the subfolder`() =
        runTest {
            open().update { it.copy(places = mapOf(uuid to soft)) }

            val entry = places().getValue(uuid.value).jsonObject
            entry.keys shouldBe setOf("project", "subfolder")
            entry.getValue("subfolder").jsonPrimitive.content shouldBe "soft"
        }

    @Test
    fun `a subfolder place and a root place survive a restart`() =
        runTest {
            open().update { it.copy(places = mapOf(uuid to soft, short to root)) }

            open().settings.value.places shouldBe mapOf(uuid to soft, short to root)
        }

    @Test
    fun `places are written sorted by key`() =
        runTest {
            val keys = listOf("ccc", "aaa", "bbb").map(::SessionId)

            open().update { it.copy(places = keys.associateWith { root }) }

            places().keys.toList() shouldBe listOf("aaa", "bbb", "ccc")
        }

    @Test
    fun `place without project makes the file broken`() =
        runTest {
            write("""{"aaa":{"subfolder":"soft"}}""")

            open().settings.value shouldBe SessionHistorySettings()

            isBroken() shouldBe true
        }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["a/b", "a\\\\b", ".", "..", "../x"])
    fun `subfolder with a slash, a backslash or dots makes the file broken`(name: String) =
        runTest {
            write("""{"aaa":{"project":"p1","subfolder":"$name"}}""")

            open().settings.value shouldBe SessionHistorySettings()

            isBroken() shouldBe true
        }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "  "])
    fun `blank subfolder makes the file broken`(name: String) =
        runTest {
            write("""{"aaa":{"project":"p1","subfolder":"$name"}}""")

            open().settings.value shouldBe SessionHistorySettings()

            isBroken() shouldBe true
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            """{"aaa":"p1"}""",
            """{"aaa":{"project":7}}""",
            """{"aaa":{"project":"p1","subfolder":5}}""",
            """["aaa"]""",
            """{"aaa":{"project":""}}""",
        ],
    )
    fun `places of the wrong shape make the file broken`(places: String) =
        runTest {
            write(places)

            open().settings.value shouldBe SessionHistorySettings()

            isBroken() shouldBe true
        }

    @Test
    fun `file without places reads empty places`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"maxAge":"DAYS_7"}}""")

            open().settings.value.places shouldBe emptyMap()
        }

    @Test
    fun `an entry for a project that is gone is read as it is`() =
        runTest {
            write("""{"aaa":{"project":"gone","subfolder":"soft"}}""")

            open().settings.value.places shouldBe
                mapOf(SessionId("aaa") to StoredPlace(ProjectId("gone"), PlaceAssignment.Subfolder("soft")))
        }

    @Test
    fun `changing the age keeps the places`() =
        runTest {
            val repo = open()
            repo.update { it.copy(places = mapOf(uuid to soft)) }

            repo.update { it.copy(maxAge = HistoryAge.DAYS_90) }

            data().keys shouldBe setOf("maxAge", "places")
            open().settings.value shouldBe
                SessionHistorySettings(maxAge = HistoryAge.DAYS_90, places = mapOf(uuid to soft))
        }

    @Test
    fun `an unused entry stays in the file when another place is saved`() =
        runTest {
            val repo = open()
            repo.update { it.copy(places = mapOf(uuid to soft)) }

            repo.update { it.copy(places = it.places + (short to root)) }

            places().keys shouldBe setOf(uuid.value, short.value)
        }
}
