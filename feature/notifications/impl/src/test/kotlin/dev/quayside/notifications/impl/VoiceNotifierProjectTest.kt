package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [VoiceNotifier] and the voice of a project (FR-N7): the voice is chosen when the message is spoken. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceNotifierProjectTest {
    private class RecordingSpeech : SpeechPort {
        val spoken = mutableListOf<Utterance>()

        val texts: List<String> get() = spoken.map { it.text }

        override suspend fun speak(utterance: Utterance): Outcome<Unit, SpeechError> {
            spoken += utterance
            return Outcome.Ok(Unit)
        }
    }

    private val speech = RecordingSpeech()
    private val settings = MutableStateFlow(NotificationSettings())

    private fun TestScope.notifier() = VoiceNotifier(speech, settings, backgroundScope, { ctx() }, RecordingLogger())

    private fun delivery(
        name: String,
        id: String,
        project: ProjectId?,
    ) = Delivery(
        NotificationChannel.VOICE,
        NotificationKind.NEEDS_INPUT,
        aSession(id = id, project = project, name = name),
    )

    private fun voices(vararg entries: Pair<ProjectId, ProjectNotifications>) {
        settings.value = settings.value.copy(projects = mapOf(*entries))
    }

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    @Test
    fun `project voice speaks its session`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", P1), "p")
            runCurrent()
            pass(21.seconds)
            notifier.offer(delivery("b", "s2", P2), "p")
            runCurrent()

            speech.spoken.map { it.voice } shouldBe listOf(ANNA, VoiceId("Zosia"))
        }

    @Test
    fun `project voice keeps the global rate and volume`() =
        runTest {
            settings.value = NotificationSettings(speech = SpeechSettings(rate = 250, volume = 30))
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", P1), "p")
            runCurrent()

            speech.spoken.single().let {
                it.voice shouldBe ANNA
                it.rate shouldBe 250
                it.volume shouldBe 30
            }
        }

    @Test
    fun `project exception without a voice speaks with the global voice`() =
        runTest {
            voices(P1 to ProjectNotifications(ProjectProfile.ERRORS_ONLY, voice = null))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", P1), "p")
            runCurrent()

            speech.spoken.single().voice shouldBe VoiceId("Zosia")
        }

    @Test
    fun `project voice replaces the system voice too`() =
        runTest {
            settings.value = NotificationSettings(speech = SpeechSettings(voice = null))
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", P1), "p")
            runCurrent()

            speech.spoken.single().voice shouldBe ANNA
        }

    @Test
    fun `a session without a project speaks with the global voice`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", null), "p")
            runCurrent()

            speech.spoken.single().voice shouldBe VoiceId("Zosia")
        }

    @Test
    fun `a terminal bell speaks with the global voice`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()

            notifier.offer(Delivery(NotificationChannel.VOICE, NotificationKind.TERMINAL_BELL, null), null)
            runCurrent()

            speech.spoken.single().voice shouldBe VoiceId("Zosia")
        }

    @Test
    fun `grouped message of two projects uses the global voice`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA), P2 to ProjectNotifications(voice = VoiceId("Ewa")))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1", P3), "p")
            runCurrent()

            notifier.offer(delivery("b", "s2", P1), "p")
            notifier.offer(delivery("c", "s3", P2), "p")
            pass(20.seconds)

            speech.texts.last() shouldBe "2 agenty czekają"
            speech.spoken.last().voice shouldBe VoiceId("Zosia")
        }

    @Test
    fun `grouped message of one project uses the project voice`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1", P3), "p")
            runCurrent()

            notifier.offer(delivery("b", "s2", P1), "p")
            notifier.offer(delivery("c", "s3", P1), "p")
            pass(20.seconds)

            speech.texts.last() shouldBe "2 agenty czekają"
            speech.spoken.last().voice shouldBe ANNA
        }

    @Test
    fun `project voice change before speaking is used`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1", P3), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2", P1), "p")

            voices(P1 to ProjectNotifications(voice = ANNA))
            pass(20.seconds)

            speech.texts.size shouldBe 2
            speech.spoken.last().voice shouldBe ANNA
        }

    @Test
    fun `project voice removed before speaking gives the global voice`() =
        runTest {
            voices(P1 to ProjectNotifications(voice = ANNA))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1", P3), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2", P1), "p")

            voices()
            pass(20.seconds)

            speech.spoken.last().voice shouldBe VoiceId("Zosia")
        }

    @Test
    fun `project voice change before a queued message is spoken is used`() =
        runTest {
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", P1), "p")
            voices(P1 to ProjectNotifications(voice = ANNA))
            runCurrent()

            speech.spoken.single().voice shouldBe ANNA
        }

    private companion object {
        val ANNA = VoiceId("Anna")
        val P1 = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
        val P2 = ProjectId("9b1d7e42-0c55-4c0e-8f3a-5d2e6a4b9c22")
        val P3 = ProjectId("c4a8f0d1-73be-4a59-b6e2-1f9d8c3a5e33")
    }
}
