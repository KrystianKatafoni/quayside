package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.BrokenFile
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.summaries.api.SummarySettingsError
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Start-up mapping, failed saves and the file written by [JsonSummarySettingsRepo]. */
class JsonSummarySettingsRepoTest {
    @TempDir
    lateinit var dir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private val store = InMemoryJsonStore(SummarySettings())
    private val path = Path.of("/tmp/summaries.json")

    @AfterEach
    fun closeDispatcher() = io.close()

    @Test
    fun `the stored switch is loaded at start and is what the app started with`(): Unit =
        runTest {
            val repo = JsonSummarySettingsRepo.open(InMemoryJsonStore(SummarySettings(useModel = true)), logger)

            repo.useModel.value shouldBe true
            repo.startedWithModel shouldBe true
        }

    @Test
    fun `a recovered file gives model off and a warning`(): Unit =
        runTest {
            store.recoverNextLoad = BrokenFile(path, "parse")

            val repo = JsonSummarySettingsRepo.open(store, logger)

            repo.useModel.value shouldBe false
            logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    @Test
    fun `a newer file refuses changes and is never saved`(): Unit =
        runTest {
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)
            val repo = JsonSummarySettingsRepo.open(store, logger)
            store.failure = null

            repo.setUseModel(on = true) shouldBe Outcome.Err(SummarySettingsError.NEWER_SCHEMA)

            repo.useModel.value shouldBe false
            store.saves shouldBe 0
        }

    @Test
    fun `an unreadable file gives model off and still allows changes`(): Unit =
        runTest {
            store.failure = StorageError.IoFailed(path, "denied")
            val repo = JsonSummarySettingsRepo.open(store, logger)
            store.failure = null

            repo.setUseModel(on = true) shouldBe Outcome.Ok(Unit)

            repo.useModel.value shouldBe true
        }

    @Test
    fun `a failed save publishes nothing and logs without details`(): Unit =
        runTest {
            val repo = JsonSummarySettingsRepo.open(store, logger)
            store.failure = StorageError.IoFailed(path, "disk full")

            repo.setUseModel(on = true) shouldBe Outcome.Err(SummarySettingsError.WRITE_FAILED)

            repo.useModel.value shouldBe false
            logger.entries.joinToString { it.message + it.fields }.contains("disk full") shouldBe false
        }

    @Test
    fun `after a failed save the next change works again`(): Unit =
        runTest {
            val repo = JsonSummarySettingsRepo.open(store, logger)
            store.failure = StorageError.IoFailed(path, "disk full")
            repo.setUseModel(on = true)
            store.failure = null

            repo.setUseModel(on = true) shouldBe Outcome.Ok(Unit)

            repo.useModel.value shouldBe true
        }

    @Test
    fun `opening writes nothing and a change reaches summaries json for the next start`(): Unit =
        runTest {
            val storage = JsonStorage(dir, io)
            val repo = summarySettingsModule(storage, logger)
            dir.resolve("summaries.json").exists() shouldBe false

            repo.setUseModel(on = true)

            dir.resolve("summaries.json").readText().contains("\"useModel\"") shouldBe true
            summarySettingsModule(storage, logger).startedWithModel shouldBe true
            summariesModule(storage, FakeOneShotPrompt { Outcome.Ok("x") }, logger).refiner.shouldNotBeNull()
        }
}
