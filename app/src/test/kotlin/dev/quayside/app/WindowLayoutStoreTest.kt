package dev.quayside.app

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** `window.json` on a real temp directory with the real [JsonStorage] (ADR 0025). */
@OptIn(ExperimentalCoroutinesApi::class)
class WindowLayoutStoreTest {
    @TempDir
    lateinit var dir: Path

    private val logger = RecordingLogger()

    private val file get() = dir.resolve("window.json")

    private fun storeOn(root: Path = dir) = WindowLayoutStore(JsonStorage(root, UnconfinedTestDispatcher()), logger)

    private fun data(): JsonObject =
        Json
            .parseToJsonElement(file.readText())
            .jsonObject
            .getValue("data")
            .jsonObject

    @Test
    fun `missing file gives the default height`() =
        runTest {
            storeOn().load() shouldBe WindowLayout()
            storeOn().load().terminalHeight shouldBe 320f
            logger.entriesAt(LogLevel.WARN) shouldHaveSize 0
        }

    @Test
    fun `saved height survives a new store`() =
        runTest {
            storeOn().save(WindowLayout(terminalHeight = 412.5f)) shouldBe Outcome.Ok(Unit)

            storeOn().load() shouldBe WindowLayout(terminalHeight = 412.5f)
        }

    @Test
    fun `default height writes no terminalHeight key`() =
        runTest {
            storeOn().save(WindowLayout()) shouldBe Outcome.Ok(Unit)

            data() shouldBe JsonObject(emptyMap())
        }

    @Test
    fun `a height back at the default removes the key of an earlier save`() =
        runTest {
            val store = storeOn()
            store.save(WindowLayout(terminalHeight = 412.5f))

            store.save(WindowLayout())

            data() shouldBe JsonObject(emptyMap())
            store.load() shouldBe WindowLayout()
        }

    @Test
    fun `a height that differs from the default is written as a number`() =
        runTest {
            storeOn().save(WindowLayout(terminalHeight = 412.5f))

            data().getValue("terminalHeight").toString() shouldBe "412.5"
        }

    @Test
    fun `newer schema gives the default and keeps the file`() =
        runTest {
            val content = """{"schemaVersion":99,"data":{"terminalHeight":412.5}}"""
            file.writeText(content)

            storeOn().load() shouldBe WindowLayout()

            file.readText() shouldBe content
            logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    @Test
    fun `save reports a storage error when the file cannot be written`(
        @TempDir other: Path,
    ) = runTest {
        // Given a storage directory that is a regular file
        val notDirectory = Files.createFile(other.resolve("not-a-directory"))

        val result = storeOn(notDirectory).save(WindowLayout(terminalHeight = 412.5f))

        result.shouldBeInstanceOf<Outcome.Err<StorageError>>()
    }

    @Test
    fun `data that is not an object moves the file to window json broken`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":true}""")

            storeOn().load() shouldBe WindowLayout()

            dir.resolve("window.json.broken").exists() shouldBe true
            file.exists() shouldBe false
            logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    @Test
    fun `text that is not JSON moves the file to window json broken`() =
        runTest {
            file.writeText("{ this is not json")

            storeOn().load() shouldBe WindowLayout()

            dir.resolve("window.json.broken").exists() shouldBe true
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stored")
    fun `file content decides the height`(
        @Suppress("UNUSED_PARAMETER") name: String,
        data: String,
        expected: Float,
    ) = runTest {
        file.writeText("""{"schemaVersion":1,"data":$data}""")

        storeOn().load().terminalHeight shouldBe expected
    }

    companion object {
        @JvmStatic
        fun stored(): Stream<Arguments> =
            Stream.of(
                arguments("a number", """{"terminalHeight":412.5}""", 412.5f),
                arguments("missing key", "{}", 320f),
                arguments("a string", """{"terminalHeight":"412"}""", 320f),
                arguments("a boolean", """{"terminalHeight":true}""", 320f),
                arguments("null", """{"terminalHeight":null}""", 320f),
                arguments("zero", """{"terminalHeight":0}""", 320f),
                arguments("negative", """{"terminalHeight":-5}""", 320f),
                arguments("a huge number is not an error", """{"terminalHeight":1e9}""", 1e9f),
                arguments("an unknown key is skipped", """{"terminalHeight":412.5,"x":1}""", 412.5f),
            )
    }
}
