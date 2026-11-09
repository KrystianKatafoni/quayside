package dev.quayside.summaries.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.summaries.api.SummarySettingsRepo
import dev.quayside.summaries.api.SummarySettingsRepoContract
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/** [SummarySettingsRepoContract] on the real store: `summaries.json` in a temp directory (ENG-T2). */
class JsonSummarySettingsRepoContractTest : SummarySettingsRepoContract() {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    // The contract's factory is not suspending; runBlocking is allowed in tests (ENG-C3).
    @Suppress("ForbiddenMethodCall")
    override fun create(): SummarySettingsRepo =
        runBlocking { summarySettingsModule(JsonStorage(tempDir, io), NoOpLogger) }
}
