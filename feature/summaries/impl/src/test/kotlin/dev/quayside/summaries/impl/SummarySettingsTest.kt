package dev.quayside.summaries.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.Loaded
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** `summaries.json` (ADR 0019): off by default, schema version 1, `useModel` as the only key. */
class SummarySettingsTest {
    @TempDir
    lateinit var dir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private fun store() = JsonStorage(dir, io).store(summarySettingsSpec())

    @Test
    fun `missing file means model off`() =
        runTest {
            val loaded = store().load()

            loaded shouldBe Outcome.Ok(Loaded(SummarySettings(useModel = false)))
            dir.resolve("summaries.json").exists() shouldBe false
        }

    @Test
    fun `the default is model off`() {
        SummarySettings().useModel shouldBe false
        summarySettingsSpec().default shouldBe SummarySettings(useModel = false)
    }

    @Test
    fun `round trips useModel`() =
        runTest {
            store().save(SummarySettings(useModel = true))

            store()
                .load()
                .getOrNull()
                .shouldNotBeNull()
                .value shouldBe SummarySettings(useModel = true)

            store().save(SummarySettings(useModel = false))

            store()
                .load()
                .getOrNull()
                .shouldNotBeNull()
                .value shouldBe SummarySettings(useModel = false)
        }

    @Test
    fun `the file is summaries json with schema version 1 and useModel`() =
        runTest {
            store().save(SummarySettings(useModel = true))

            val file = Json.parseToJsonElement(dir.resolve("summaries.json").readText()).jsonObject
            file.getValue("schemaVersion").jsonPrimitive.int shouldBe 1
            file
                .getValue("data")
                .jsonObject
                .getValue("useModel")
                .jsonPrimitive.boolean shouldBe true
        }

    @Test
    fun `useModel as the string true is a wrong type and gives the default`() =
        runTest {
            // The string "true" is not the boolean true: the file is broken: moved aside, default applies.
            dir.resolve("summaries.json").writeText("""{"schemaVersion":1,"data":{"useModel":"true"}}""")

            val loaded = store().load().getOrNull().shouldNotBeNull()

            loaded.value shouldBe SummarySettings(useModel = false)
            loaded.recovered.shouldNotBeNull()
            dir.resolve("summaries.json.broken").exists() shouldBe true
        }
}
