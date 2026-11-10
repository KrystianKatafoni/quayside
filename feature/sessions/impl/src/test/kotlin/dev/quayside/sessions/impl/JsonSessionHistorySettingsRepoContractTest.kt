package dev.quayside.sessions.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.sessions.api.SessionHistorySettingsContract
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/** [SessionHistorySettingsContract] on the real store: `session-history.json` in a temp directory (ENG-T2). */
class JsonSessionHistorySettingsRepoContractTest : SessionHistorySettingsContract() {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    override suspend fun create(): SessionHistorySettingsRepo =
        sessionHistorySettingsModule(JsonStorage(tempDir, io), NoOpLogger)
}
