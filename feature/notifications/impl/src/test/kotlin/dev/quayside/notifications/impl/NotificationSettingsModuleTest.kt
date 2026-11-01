package dev.quayside.notifications.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The factory against a real directory: settings written by one instance are read by the next. */
class NotificationSettingsModuleTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    fun `settings persist across instances`() =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            notificationSettingsModule(storage, NoOpLogger).update { it.copy(runningLongMinutes = 12, voice = false) }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.runningLongMinutes shouldBe 12
            reopened.voice shouldBe false
            Files.exists(tempDir.resolve("notifications.json")) shouldBe true
        }
}
