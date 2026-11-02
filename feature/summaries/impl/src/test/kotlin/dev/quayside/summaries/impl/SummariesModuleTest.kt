package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.summaries.api.SummariesApi
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
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

/** The factory against a real directory: `summaries.json` decides whether a model summary exists. */
class SummariesModuleTest {
    @TempDir
    lateinit var dir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val prompt = FakeOneShotPrompt { Outcome.Ok("Model says hi.") }
    private val file: Path get() = dir.resolve("summaries.json")

    @AfterEach
    fun closeDispatcher() = io.close()

    private suspend fun module(logger: RecordingLogger? = null): SummariesApi =
        summariesModule(JsonStorage(dir, io), prompt, logger ?: RecordingLogger())

    @Test
    fun `useModel true gives a refiner`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"useModel":true}}""")

            val refiner = module().refiner.shouldNotBeNull()

            refiner.refine("Fixed the login form.") shouldBe Outcome.Ok("Model says hi.")
            prompt.calls.size shouldBe 1
        }

    @Test
    fun `unknown keys in the file are ignored`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"useModel":true,"future":[1,2]}}""")

            module().refiner.shouldNotBeNull()
        }

    @Test
    fun `missing file gives no refiner and the quick summary still works`() =
        runTest {
            val api = module()

            api.refiner.shouldBeNull()
            api.summarizer.summarize("Fixed the login form. Added a test. Updated docs.") shouldBe
                Outcome.Ok("Fixed the login form. Added a test.")
            file.exists() shouldBe false
        }

    @Test
    fun `useModel false gives no refiner and the model is never asked`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"useModel":false}}""")

            val api = module()
            api.summarizer.summarize("Fixed the login form.")

            api.refiner.shouldBeNull()
            prompt.calls.shouldBeEmpty()
        }

    @Test
    fun `broken file gives no refiner and is moved aside`() =
        runTest {
            file.writeText("this is not json {")

            module().refiner.shouldBeNull()

            dir.resolve("summaries.json.broken").exists() shouldBe true
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"schemaVersion":1,"data":{"useModel":"yes"}}""",
            """{"schemaVersion":1,"data":{"useModel":"true"}}""",
            """{"schemaVersion":1,"data":[true]}""",
            """{"schemaVersion":1}""",
        ],
    )
    fun `a file with the wrong shape never switches the model on`(content: String) =
        runTest {
            file.writeText(content)

            module().refiner.shouldBeNull()
        }

    @Test
    fun `useModel as the string true is a wrong type, not true`() =
        runTest {
            // The string "true" is not the boolean true: the file is broken: moved aside, model off.
            file.writeText("""{"schemaVersion":1,"data":{"useModel":"true"}}""")

            module().refiner.shouldBeNull()

            dir.resolve("summaries.json.broken").exists() shouldBe true
        }

    @Test
    fun `file of a newer version gives no refiner and stays as it is`() =
        runTest {
            val content = """{"schemaVersion":99,"data":{"useModel":true}}"""
            file.writeText(content)

            module().refiner.shouldBeNull()

            file.readText() shouldBe content
        }

    @Test
    fun `the file is read once at start`() =
        runTest {
            file.writeText("""{"schemaVersion":1,"data":{"useModel":false}}""")
            val api = module()

            file.writeText("""{"schemaVersion":1,"data":{"useModel":true}}""")

            api.refiner.shouldBeNull()
        }

    @Test
    fun `the log holds no file content`() =
        runTest {
            val logger = RecordingLogger()
            file.writeText("""{"schemaVersion":1,"data":{"useModel":"secret-value"}}""")

            module(logger)

            logger.entries.filter { it.level >= LogLevel.INFO }.forEach { entry ->
                "${entry.message} ${entry.fields} ${entry.cause}" shouldNotContain "secret-value"
            }
        }
}
