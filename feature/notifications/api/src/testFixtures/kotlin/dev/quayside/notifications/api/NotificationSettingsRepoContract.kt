package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

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
                setOf(NotificationChannel.POPUP, NotificationChannel.SOUND, NotificationChannel.VOICE)
            repo.settings.value.channelsFor(NotificationKind.FAILED) shouldBe NotificationChannel.entries.toSet()
        }

    @Test
    public fun `update keeps speech settings`(): Unit =
        runTest {
            val repo = create()
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

            repo.update { it.copy(speech = speech) }
            repo.update { it.copy(voice = false) }

            repo.settings.value.speech shouldBe speech
        }

    @Test
    public fun `update keeps quiet hours`(): Unit =
        runTest {
            val repo = create()
            val quiet = QuietHours(true, LocalTime.of(21, 30), LocalTime.of(6, 15), respectFocus = false)

            repo.update { it.copy(quietHours = quiet) }
            repo.update { it.copy(voice = false) }

            repo.settings.value.quietHours shouldBe quiet
        }

    @Test
    public fun `update keeps project exceptions`(): Unit =
        runTest {
            val repo = create()
            val projects =
                mapOf(
                    ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11") to
                        ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Anna")),
                    ProjectId("9b1d7e42-0c55-4c0e-8f3a-5d2e6a4b9c22") to ProjectNotifications(ProjectProfile.MUTED),
                )

            repo.update { it.copy(projects = projects) }
            repo.update { it.copy(voice = false) }

            repo.settings.value.projects shouldBe projects
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
