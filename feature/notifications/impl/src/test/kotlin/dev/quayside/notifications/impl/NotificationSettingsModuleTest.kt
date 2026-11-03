package dev.quayside.notifications.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime

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

    @Test
    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    fun `quiet hours survive reopening`(): Unit =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            val quiet = QuietHours(true, LocalTime.of(21, 30), LocalTime.of(6, 15), respectFocus = false)
            notificationSettingsModule(storage, NoOpLogger).update { it.copy(quietHours = quiet) }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.quietHours shouldBe quiet
        }

    @Test
    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    fun `project exceptions survive reopening`(): Unit =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            val first = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
            val second = ProjectId("9b1d7e42-0c55-4c0e-8f3a-5d2e6a4b9c22")
            val projects =
                mapOf(
                    first to ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Anna")),
                    second to ProjectNotifications(ProjectProfile.MUTED),
                )
            notificationSettingsModule(storage, NoOpLogger).update { it.copy(projects = projects) }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.projects shouldBe projects
            reopened.projectFor(first).voice shouldBe VoiceId("Anna")
            Files.readString(tempDir.resolve("notifications.json")) shouldContain first.value
        }

    @Test
    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    fun `a default exception is not kept in the file`(): Unit =
        runBlocking {
            val storage = JsonStorage(tempDir, Dispatchers.IO)
            val id = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
            notificationSettingsModule(storage, NoOpLogger).update {
                it.copy(projects = mapOf(id to ProjectNotifications()))
            }

            val reopened = notificationSettingsModule(storage, NoOpLogger).settings.value

            reopened.projectFor(id) shouldBe ProjectNotifications()
            Files.readString(tempDir.resolve("notifications.json")) shouldNotContain id.value
        }
}
