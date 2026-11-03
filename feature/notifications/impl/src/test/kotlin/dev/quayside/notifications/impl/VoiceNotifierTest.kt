package dev.quayside.notifications.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [VoiceNotifier] on virtual time: templates, the 20 s rule and the second check of the context (FR-N4, FR-N5). */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceNotifierTest {
    private class RecordingSpeech(
        private val result: Outcome<Unit, SpeechError> = Outcome.Ok(Unit),
    ) : SpeechPort {
        val spoken = mutableListOf<Utterance>()

        val texts: List<String> get() = spoken.map { it.text }

        override suspend fun speak(utterance: Utterance): Outcome<Unit, SpeechError> {
            spoken += utterance
            return result
        }
    }

    private val speech = RecordingSpeech()
    private val logger = RecordingLogger()
    private val settings = MutableStateFlow(NotificationSettings())
    private var context = ctx()

    private fun TestScope.notifier(
        port: SpeechPort = speech,
        scope: CoroutineScope = backgroundScope,
    ) = VoiceNotifier(port, settings, scope, { context }, logger)

    private fun delivery(
        name: String,
        id: String,
        kind: NotificationKind = NotificationKind.NEEDS_INPUT,
        state: SessionState = SessionState.AwaitingInput(null),
    ) = Delivery(NotificationChannel.VOICE, kind, aSession(id = id, name = name, state = state))

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    @Test
    fun `speaks a needs input message`() =
        runTest {
            val notifier = notifier()

            notifier.offer(delivery("opcua-connector", "s1"), "plantpulse-core")
            runCurrent()

            speech.spoken shouldBe
                listOf(
                    Utterance(
                        "Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź",
                        VoiceId("Zosia"),
                        190,
                        70,
                    ),
                )
        }

    @Test
    fun `speaks with the voice rate and volume of the settings`() =
        runTest {
            settings.value = NotificationSettings(speech = SpeechSettings(voice = null, rate = 250, volume = 30))
            val notifier = notifier()

            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()

            speech.spoken.single().let {
                it.voice shouldBe null
                it.rate shouldBe 250
                it.volume shouldBe 30
            }
        }

    @Test
    fun `reads the question after the message`() =
        runTest {
            val notifier = notifier()

            notifier.offer(delivery("a", "s1", state = SessionState.AwaitingInput("Which branch?")), "p")
            runCurrent()

            speech.texts.single() shouldBe "Agent a w projekcie p czeka na odpowiedź. Which branch?"
        }

    @Test
    fun `a message that renders to nothing is not spoken`() =
        runTest {
            settings.value =
                NotificationSettings(
                    speech =
                        SpeechSettings(
                            templates =
                                mapOf(NotificationKind.NEEDS_INPUT to "{pytanie}"),
                        ),
                )
            val notifier = notifier()

            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()

            speech.spoken shouldBe emptyList()
        }

    @Test
    fun `three sessions within 20 s give one grouped message after 20 s`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()

            pass(1.seconds)
            notifier.offer(delivery("b", "s2"), "p")
            notifier.offer(delivery("c", "s3"), "p")
            notifier.offer(delivery("d", "s4"), "p")
            pass(18.seconds)

            speech.texts.size shouldBe 1
            pass(1.seconds + 1.seconds)

            speech.texts.size shouldBe 2
            speech.texts.last() shouldBe "3 agenty czekają"
        }

    @Test
    fun `one waiting message is spoken as it is after 20 s`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()

            notifier.offer(delivery("b", "s2"), "p")
            pass(19.seconds)
            speech.texts.size shouldBe 1
            pass(1.seconds)

            speech.texts.last() shouldBe "Agent b w projekcie p czeka na odpowiedź"
        }

    @Test
    fun `next message after 20 s is spoken alone`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()

            pass(21.seconds)
            notifier.offer(delivery("b", "s2"), "p")
            runCurrent()

            speech.texts shouldBe
                listOf("Agent a w projekcie p czeka na odpowiedź", "Agent b w projekcie p czeka na odpowiedź")
        }

    @Test
    fun `a spoken group starts a new cooldown`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")
            pass(20.seconds)
            val spokenAfterGroup = speech.texts.size

            notifier.offer(delivery("c", "s3"), "p")
            pass(19.seconds)

            spokenAfterGroup shouldBe 2
            speech.texts.size shouldBe 2
            pass(1.seconds)
            speech.texts.last() shouldBe "Agent c w projekcie p czeka na odpowiedź"
        }

    @Test
    fun `group is dropped when the app became active`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")
            notifier.offer(delivery("c", "s3"), "p")

            context = ctx(appActive = true)
            pass(25.seconds)

            speech.texts.size shouldBe 1
        }

    @Test
    fun `dropping a group clears the cooldown so the next message is spoken at once`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")
            context = ctx(appActive = true)
            pass(20.seconds)
            speech.texts.size shouldBe 1

            context = ctx()
            notifier.offer(delivery("c", "s3"), "p")
            runCurrent()

            speech.texts shouldBe
                listOf("Agent a w projekcie p czeka na odpowiedź", "Agent c w projekcie p czeka na odpowiedź")
        }

    @Test
    fun `group is dropped when the microphone became active`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            context = ctx(microphoneActive = true)
            pass(25.seconds)

            speech.texts.size shouldBe 1
        }

    @Test
    fun `group is dropped when quiet hours start during the cooldown`() =
        runTest {
            settings.value = settings.value.copy(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            context = ctx(localTime = LocalTime.of(22, 0))
            pass(25.seconds)

            speech.texts.size shouldBe 1
        }

    @Test
    fun `after a group dropped by quiet hours the next message is spoken at once`() =
        runTest {
            settings.value = settings.value.copy(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")
            context = ctx(localTime = LocalTime.of(23, 0))
            pass(20.seconds)

            context = ctx(localTime = LocalTime.of(7, 0))
            notifier.offer(delivery("c", "s3"), "p")
            runCurrent()

            speech.texts.size shouldBe 2
            speech.texts.last() shouldBe "Agent c w projekcie p czeka na odpowiedź"
        }

    @Test
    fun `group is spoken when the hours do not cover the time`() =
        runTest {
            settings.value = settings.value.copy(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")
            notifier.offer(delivery("c", "s3"), "p")

            pass(20.seconds)

            speech.texts.size shouldBe 2
        }

    @Test
    fun `group is dropped when a Focus started during the cooldown`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            context = ctx(focusActive = true)
            pass(25.seconds)

            speech.texts.size shouldBe 1
        }

    @Test
    fun `group is dropped when voice was switched off`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            settings.value = settings.value.copy(voice = false)
            pass(25.seconds)

            speech.texts.size shouldBe 1
        }

    @Test
    fun `speech error is logged without text`() =
        runTest {
            val failing = RecordingSpeech(Outcome.Err(SpeechError.Failed(1)))
            val notifier = notifier(failing)

            notifier.offer(
                delivery("secret-session", "s1", state = SessionState.AwaitingInput("secret question")),
                "secret-project",
            )
            runCurrent()

            val warnings = logger.entriesAt(LogLevel.WARN)
            warnings.isNotEmpty() shouldBe true
            val logged = warnings.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain "secret"
            logged shouldNotContain "question"
        }

    @Test
    fun `speech error does not stop the next message`() =
        runTest {
            val failing = RecordingSpeech(Outcome.Err(SpeechError.TimedOut))
            val notifier = notifier(failing)
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            pass(20.seconds)

            failing.texts.size shouldBe 2
        }

    @Test
    fun `cancelling the scope stops waiting`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val notifier = notifier(scope = scope)
            notifier.offer(delivery("a", "s1"), "p")
            runCurrent()
            notifier.offer(delivery("b", "s2"), "p")

            scope.cancel()
            pass(60.seconds)

            speech.texts.size shouldBe 1
        }
}
