package dev.quayside.notifications.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettingsRepoContract
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/** [NotificationSettingsRepoContract] on the real store: `notifications.json` in a temp directory (ENG-T2). */
class JsonNotificationSettingsRepoContractTest : NotificationSettingsRepoContract() {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    // The contract's factory is not suspending; runBlocking is allowed in tests (ENG-C3).
    @Suppress("ForbiddenMethodCall")
    override fun create(): NotificationSettingsRepo =
        runBlocking { notificationSettingsModule(JsonStorage(tempDir, io), NoOpLogger) }
}
