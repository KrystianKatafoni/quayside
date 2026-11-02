package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scenario at module level (docs/12 §4): session events through `notificationCenterModule` end as a `say` call.
 * Given a session that waits, When the center gets the event, Then `say` is started with the filled template.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFlowTest {
    private val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private val runner = FakeProcessRunner()
    private val appActive = MutableStateFlow(false)
    private val repo = FakeNotificationSettingsRepo()

    private fun TestScope.start(
        process: FakeProcessRunner? = runner,
        settings: FakeNotificationSettingsRepo = repo,
    ) = notificationCenterModule(
        settings = settings,
        scope = backgroundScope,
        clock = TestClock(),
        events = events,
        projectName = { id: ProjectId? -> id?.value },
        process = process,
        appActive = appActive,
    )

    private fun waiting(
        name: String,
        id: String = "s-$name",
    ) = SessionEvent.BecameBlocked(
        aSession(
            id = id,
            name = name,
            project = ProjectId("plantpulse-core"),
            state = SessionState.AwaitingInput(null),
        ),
    )

    private suspend fun TestScope.send(event: SessionEvent) {
        events.emit(event)
        runCurrent()
    }

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private val spoken get() = runner.commands.map { it.argv.last() }

    @Test
    fun `a session that waits is announced by say`() =
        runTest {
            start()

            send(waiting("opcua-connector"))

            runner.commands.single().argv.let {
                it.first() shouldBe "/usr/bin/say"
                it.last() shouldBe "[[volm 0.70]] Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź"
            }
            runner.commands.single().argv shouldBe
                listOf(
                    "/usr/bin/say",
                    "-v",
                    "Zosia",
                    "-r",
                    "190",
                    "[[volm 0.70]] Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź",
                )
        }

    @Test
    fun `an active app window means no say`() =
        runTest {
            appActive.value = true
            start()

            send(waiting("opcua-connector"))
            pass(30.seconds)

            runner.commands shouldBe emptyList()
        }

    @Test
    fun `say is used again when the window loses focus`() =
        runTest {
            appActive.value = true
            start()
            send(waiting("a"))

            appActive.value = false
            send(waiting("b"))

            spoken.size shouldBe 1
        }

    @Test
    fun `the foreground switch off lets say speak in the active app`() =
        runTest {
            appActive.value = true
            val settings =
                FakeNotificationSettingsRepo(
                    NotificationSettings(speech = SpeechSettings(onlyInBackground = false)),
                )
            start(settings = settings)

            send(waiting("a"))

            runner.commands.size shouldBe 1
        }

    @Test
    fun `the voice switch off means no say`() =
        runTest {
            start(settings = FakeNotificationSettingsRepo(NotificationSettings(voice = false)))

            send(waiting("a"))
            pass(30.seconds)

            runner.commands shouldBe emptyList()
        }

    @Test
    fun `several sessions within 20 s are announced together`() =
        runTest {
            start()

            send(waiting("a"))
            send(waiting("b"))
            send(waiting("c"))
            send(waiting("d"))
            pass(20.seconds)

            spoken shouldBe
                listOf(
                    "[[volm 0.70]] Agent a w projekcie plantpulse-core czeka na odpowiedź",
                    "[[volm 0.70]] 3 agenty czekają",
                )
        }

    @Test
    fun `the voice of the settings is used`() =
        runTest {
            val settings =
                FakeNotificationSettingsRepo(
                    NotificationSettings(speech = SpeechSettings(voice = VoiceId("Ewa"), rate = 250, volume = 100)),
                )
            start(settings = settings)

            send(waiting("a"))

            runner.commands
                .single()
                .argv
                .drop(1)
                .take(4) shouldBe listOf("-v", "Ewa", "-r", "250")
            spoken.single().startsWith("[[volm 1.00]]") shouldBe true
        }

    @Test
    fun `a failing say does not stop the next message`() =
        runTest {
            val failing = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/say")) }
            start(process = failing)

            send(waiting("a"))
            send(waiting("b"))
            pass(20.seconds)

            failing.commands.size shouldBe 2
        }

    @Test
    fun `without a process runner nothing is spoken and the popups still work`() =
        runTest {
            val center = start(process = null)

            send(waiting("a"))

            center.popups.value.size shouldBe 1
            runner.commands shouldBe emptyList()
        }
}
