package dev.quayside.notifications.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.BrokenFile
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Start-up mapping and failed saves of [JsonNotificationSettingsRepo] over [InMemoryJsonStore]. */
class JsonNotificationSettingsRepoTest {
    private val logger = RecordingLogger()
    private val store = InMemoryJsonStore(NotificationSettings())
    private val path = Path.of("/tmp/notifications.json")

    @Test
    fun `stored settings are loaded at start`(): Unit =
        runTest {
            val stored = NotificationSettings(voice = false)
            val repo = JsonNotificationSettingsRepo.open(InMemoryJsonStore(stored), logger)

            repo.settings.value shouldBe stored
        }

    @Test
    fun `a recovered file gives the defaults and a warning`(): Unit =
        runTest {
            store.recoverNextLoad = BrokenFile(path, "parse")

            val repo = JsonNotificationSettingsRepo.open(store, logger)

            repo.settings.value shouldBe NotificationSettings()
            logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    @Test
    fun `a newer file refuses updates and is never saved`(): Unit =
        runTest {
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)
            val repo = JsonNotificationSettingsRepo.open(store, logger)
            store.failure = null

            repo.update { it.copy(voice = false) } shouldBe Outcome.Err(NotificationSettingsError.NEWER_SCHEMA)

            repo.settings.value shouldBe NotificationSettings()
            store.saves shouldBe 0
        }

    @Test
    fun `an unreadable file gives the defaults and still allows updates`(): Unit =
        runTest {
            store.failure = StorageError.IoFailed(path, "denied")
            val repo = JsonNotificationSettingsRepo.open(store, logger)
            store.failure = null

            repo.update { it.copy(voice = false) } shouldBe Outcome.Ok(Unit)

            repo.settings.value.voice shouldBe false
        }

    @Test
    fun `a failed save publishes nothing and logs without details`(): Unit =
        runTest {
            val repo = JsonNotificationSettingsRepo.open(store, logger)
            store.failure = StorageError.IoFailed(path, "disk full")

            repo.update { it.copy(voice = false) } shouldBe Outcome.Err(NotificationSettingsError.WRITE_FAILED)

            repo.settings.value shouldBe NotificationSettings()
            logger.entries.joinToString { it.message + it.fields }.contains("disk full") shouldBe false
        }

    @Test
    fun `after a failed save the next update works again`(): Unit =
        runTest {
            val repo = JsonNotificationSettingsRepo.open(store, logger)
            store.failure = StorageError.IoFailed(path, "disk full")
            repo.update { it.copy(voice = false) }
            store.failure = null

            repo.update { it.copy(voice = false) } shouldBe Outcome.Ok(Unit)

            repo.settings.value.voice shouldBe false
        }
}
