package dev.agentmanager.core.storage

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.testing.RecordingLogger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Integration tests on real files in a temp directory (docs/12: `JsonStore`). */
class FileJsonStoreTest : JsonStoreContract() {
    // Real threads: file IO is blocking and does not run on virtual time.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()

    @AfterEach
    fun closeDispatcher() = io.close()

    override fun create(
        dir: Path,
        default: String,
    ): JsonStore<String> = JsonStorage(dir, io, logger).store(StoreSpec("value.json", String.serializer(), default))

    private val names = MapSerializer(String.serializer(), String.serializer())

    private fun namesStore(
        schemaVersion: Int = 1,
        migrations: List<Migration> = emptyList(),
    ) = JsonStorage(
        dir,
        io,
        logger,
    ).store(StoreSpec("names.json", names, mapOf("name" to "default"), schemaVersion, migrations))

    private val file get() = dir.resolve("names.json")

    @Test
    fun `writes the schema version next to the data and leaves no temp file`() =
        runTest {
            namesStore(schemaVersion = 1).save(mapOf("name" to "x")) shouldBe Outcome.Ok(Unit)

            file.readText().replace(Regex("\\s"), "") shouldBe """{"schemaVersion":1,"data":{"name":"x"}}"""
            dir.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf("names.json")
        }

    @Test
    fun `creates the storage directory on the first save`() =
        runTest {
            val nested = dir.resolve("a/b")
            val store = JsonStorage(nested, io).store(StoreSpec("v.json", String.serializer(), ""))

            store.save("x") shouldBe Outcome.Ok(Unit)

            nested.resolve("v.json").exists() shouldBe true
        }

    @Test
    fun `moves an unparseable file aside and returns the default once`() =
        runTest {
            file.writeText("{ not json")
            val store = namesStore()

            val loaded = store.load().getOrNull().shouldNotBeNull()

            loaded.value shouldBe mapOf("name" to "default")
            loaded.recovered.shouldNotBeNull().movedTo shouldBe dir.resolve("names.json.broken")
            dir.resolve("names.json.broken").readText() shouldBe "{ not json"
            logger.entriesAt(LogLevel.WARN).map { it.message } shouldBe listOf("broken config file moved aside")
            store.load() shouldBe Outcome.Ok(Loaded(mapOf("name" to "default")))
        }

    @Test
    fun `moves aside every kind of broken file with a reason that never quotes it`() =
        runTest {
            val secret = "/Users/secret-user/project"
            val broken =
                mapOf(
                    """{"data":{"name":"$secret"}}""" to "missing schemaVersion",
                    """{"schemaVersion":"one","data":{"name":"$secret"}}""" to "missing schemaVersion",
                    """{"schemaVersion":0,"data":{"name":"$secret"}}""" to "invalid schemaVersion",
                    """{"schemaVersion":3,"name":"$secret"}""" to "missing data",
                    """{"schemaVersion":3,"data":{"name":["$secret"]}}""" to "data does not match type",
                    """{"schemaVersion":2,"data":["$secret"]}""" to "migration from v2 failed",
                    """{"schemaVersion":1,"data":{"name":"$secret"}}""" to "migration from v1 failed",
                    """["$secret"]""" to "not a JSON object",
                    """{ "$secret" """ to "invalid JSON",
                )
            val failing = Migration(1) { error("bug in migration: ${it.values}") }
            val identity = Migration(2) { it }
            for ((content, reason) in broken) {
                file.writeText(content)

                val loaded = namesStore(schemaVersion = 3, migrations = listOf(failing, identity)).load()

                loaded.getOrNull()?.recovered?.reason shouldBe reason
                dir.resolve("names.json.broken").readText() shouldBe content
            }
            logger.entries.toString().contains("secret-user") shouldBe false
        }

    @Test
    fun `moves aside a file that is not valid UTF-8`() =
        runTest {
            Files.write(file, byteArrayOf(0xC3.toByte(), 0x28))

            namesStore()
                .load()
                .getOrNull()
                ?.recovered
                ?.reason shouldBe "invalid UTF-8"
            dir.resolve("names.json.broken").exists() shouldBe true
        }

    @Test
    fun `refuses a file from a newer schema and leaves it untouched`() =
        runTest {
            val newer = """{"schemaVersion":3,"payload":{"name":"x"}}"""
            file.writeText(newer)

            namesStore(schemaVersion = 1).load() shouldBe
                Outcome.Err(StorageError.NewerSchema(found = 3, supported = 1))
            namesStore(schemaVersion = 1).update { it } shouldBe Outcome.Err(StorageError.NewerSchema(3, 1))

            file.readText() shouldBe newer
        }

    @Test
    fun `migrates an older schema step by step and writes it back`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"name":"x"}}""")
            val renameKey = Migration(1) { JsonObject(mapOf("displayName" to it.getValue("name"))) }
            val upperCase =
                Migration(2) { data ->
                    JsonObject(
                        data.mapValues {
                            JsonPrimitive(
                                it.value.jsonPrimitive.content
                                    .uppercase(),
                            )
                        },
                    )
                }

            val loaded = namesStore(schemaVersion = 3, migrations = listOf(upperCase, renameKey)).load()

            loaded shouldBe Outcome.Ok(Loaded(mapOf("displayName" to "X")))
            file.readText().replace(Regex("\\s"), "") shouldBe """{"schemaVersion":3,"data":{"displayName":"X"}}"""
        }

    @Test
    fun `reports IO failures without touching the file`() =
        runTest {
            file.createDirectories()

            namesStore().load().errorOrNull().shouldBeInstanceOf<StorageError.IoFailed>()
            namesStore()
                .save(emptyMap())
                .errorOrNull()
                .shouldBeInstanceOf<StorageError.IoFailed>()
                .path shouldBe file
            dir.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf("names.json")
        }

    @Test
    fun `store spec rejects invalid definitions`() {
        shouldThrow<IllegalArgumentException> { StoreSpec(" ", String.serializer(), "") }
        shouldThrow<IllegalArgumentException> { StoreSpec("a/b.json", String.serializer(), "") }
        shouldThrow<IllegalArgumentException> { StoreSpec("a.json", String.serializer(), "", schemaVersion = 0) }
        shouldThrow<IllegalArgumentException> { StoreSpec("a.json", String.serializer(), "", schemaVersion = 2) }
        shouldThrow<IllegalArgumentException> {
            StoreSpec(
                "a.json",
                String.serializer(),
                "",
                schemaVersion = 2,
                migrations =
                    listOf(
                        Migration(1) {
                            it
                        },
                        Migration(1) { it },
                    ),
            )
        }
    }
}
