package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [NotificationSettingsRepo] must have (ENG-T2). Runs on the fake and on the file store. */
public abstract class NotificationSettingsRepoContract {
    /** A repository with nothing stored. */
    protected abstract fun create(): NotificationSettingsRepo

    @Test
    public fun `fresh repository holds the defaults`(): Unit =
        runTest {
            create().settings.value shouldBe NotificationSettings()
        }

    @Test
    public fun `update changes and publishes the settings`(): Unit =
        runTest {
            val repo = create()

            val result = repo.update { it.copy(voice = false) }

            result shouldBe Outcome.Ok(Unit)
            repo.settings.value.voice shouldBe false
        }

    @Test
    public fun `update can switch one channel of one kind`(): Unit =
        runTest {
            val repo = create()

            repo.update { it.withChannel(NotificationKind.TURN_ENDED, NotificationChannel.VOICE, enabled = true) }

            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe
                setOf(NotificationChannel.POPUP, NotificationChannel.VOICE)
            repo.settings.value.channelsFor(NotificationKind.FAILED) shouldBe NotificationChannel.entries.toSet()
        }

    @Test
    public fun `concurrent updates lose no change`(): Unit =
        runTest {
            val repo = create()

            List(CONCURRENT_UPDATES) {
                async { repo.update { it.copy(runningLongMinutes = it.runningLongMinutes + 1) } }
            }.awaitAll()

            repo.settings.value.runningLongMinutes shouldBe
                NotificationSettings.DEFAULT_RUNNING_LONG_MINUTES + CONCURRENT_UPDATES
        }

    private companion object {
        const val CONCURRENT_UPDATES = 20
    }
}
