package dev.quayside.notifications.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
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
    fun `settings persist across instances`(): Unit =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            notificationSettingsModule(storage, NoOpLogger).update { it.copy(runningLongMinutes = 12, voice = false) }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.runningLongMinutes shouldBe 12
            reopened.voice shouldBe false
            Files.exists(tempDir.resolve("notifications.json")) shouldBe true
        }

    @Test
    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    fun `speech settings survive reopening`(): Unit =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            val speech =
                SpeechSettings(
                    voice = VoiceId("Ewa"),
                    rate = 250,
                    volume = 40,
                    templates = mapOf(NotificationKind.FAILED to "Ups, {sesja}"),
                    readQuestion = false,
                    onlyInBackground = false,
                    muteWhenMicrophoneActive = false,
                )
            notificationSettingsModule(storage, NoOpLogger).update { it.copy(speech = speech) }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.speech shouldBe speech
        }
}
