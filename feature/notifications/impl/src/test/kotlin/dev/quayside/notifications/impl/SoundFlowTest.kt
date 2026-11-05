package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.QuietHours
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
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scenario at module level (docs/12 §4): session events through `notificationCenterModule` end as an `afplay` call.
 * Given a session in some state, When the center gets the event, Then `afplay` plays the sound of its kind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundFlowTest {
    private val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private val runner = FakeProcessRunner()
    private val appActive = MutableStateFlow(false)
    private val clock = TestClock()
    private val project = ProjectId("plantpulse-core")

    private fun TestScope.start(
        process: FakeProcessRunner? = runner,
        settings: NotificationSettings = NotificationSettings(),
    ) = notificationCenterModule(
        settings = FakeNotificationSettingsRepo(settings),
        scope = backgroundScope,
        clock = clock,
        zone = { WARSAW },
        events = events,
        projectName = { id: ProjectId? -> id?.value },
        process = process,
        appActive = appActive,
    )

    private fun waiting(
        name: String = "a",
        state: SessionState = SessionState.AwaitingInput(null),
    ) = SessionEvent.BecameBlocked(aSession(id = "s-$name", name = name, project = project, state = state))

    private fun failed(name: String = "f") =
        SessionEvent.Failed(aSession(id = "s-$name", name = name, project = project, state = SessionState.Failed))

    private fun turnEnded(name: String = "t") =
        SessionEvent.TurnFinished(aSession(id = "s-$name", name = name, project = project, state = SessionState.Done))

    private suspend fun TestScope.send(event: SessionEvent) {
        events.emit(event)
        runCurrent()
    }

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun played(of: FakeProcessRunner = runner): List<String> =
        of.commands
            .filter { it.argv.first() == "/usr/bin/afplay" }
            .map { it.argv.last().substringAfterLast('/') }

    @Test
    fun `waiting session plays Glass`() =
        runTest {
            start()

            send(waiting())

            val command = runner.commands.single { it.argv.first() == "/usr/bin/afplay" }
            command.argv shouldBe listOf("/usr/bin/afplay", "/System/Library/Sounds/Glass.aiff")
        }

    @Test
    fun `permission request plays Ping`() =
        runTest {
            start()

            send(waiting(state = SessionState.AwaitingPermission("Bash")))

            played() shouldBe listOf("Ping.aiff")
        }

    @Test
    fun `failed session plays Basso`() =
        runTest {
            start()

            send(failed())

            played() shouldBe listOf("Basso.aiff")
        }

    @Test
    fun `turn ended plays Hero by default`() =
        runTest {
            val center = start()

            send(turnEnded())

            played() shouldBe listOf("Hero.aiff")
            center.popups.value.size shouldBe 1
            runner.commands.none { it.argv.first() == "/usr/bin/say" } shouldBe true
        }

    @Test
    fun `turn ended is silent when the user switched its sound off`() =
        runTest {
            val settings =
                NotificationSettings().withChannel(
                    NotificationKind.TURN_ENDED,
                    NotificationChannel.SOUND,
                    false,
                )
            start(settings = settings)

            send(turnEnded())

            played() shouldBe emptyList()
        }

    @Test
    fun `a stored matrix of other kinds keeps the new default of a finished turn`() =
        runTest {
            val settings = NotificationSettings().withChannel(NotificationKind.FAILED, NotificationChannel.SOUND, false)
            start(settings = settings)

            send(turnEnded())
            send(failed())

            played() shouldBe listOf("Hero.aiff")
        }

    @Test
    fun `quiet hours play no sound but keep the popup`() =
        runTest {
            // Given quiet hours 22:00-07:00 and 22:30 in Warsaw
            clock.setTo(Instant.parse("2026-11-03T21:30:00Z"))
            val center = start(settings = NotificationSettings(quietHours = QuietHours(enabled = true)))

            // When a session waits
            send(waiting())
            pass(10.seconds)

            // Then the popup shows and no sound plays
            center.popups.value.size shouldBe 1
            played() shouldBe emptyList()

            // And at noon the same settings play again
            clock.setTo(Instant.parse("2026-11-04T11:00:00Z"))
            send(waiting("b"))
            played() shouldBe listOf("Glass.aiff")
        }

    @Test
    fun `muted project plays no sound`() =
        runTest {
            val settings =
                NotificationSettings(projects = mapOf(project to ProjectNotifications(ProjectProfile.MUTED)))
            start(settings = settings)

            send(waiting())
            send(failed())
            pass(10.seconds)

            played() shouldBe emptyList()
        }

    @Test
    fun `errors only project plays the sound of a failure only`() =
        runTest {
            val settings =
                NotificationSettings(projects = mapOf(project to ProjectNotifications(ProjectProfile.ERRORS_ONLY)))
            start(settings = settings)

            send(waiting())
            played() shouldBe emptyList()
            send(failed())

            played() shouldBe listOf("Basso.aiff")
        }

    @Test
    fun `active app still plays the sound`() =
        runTest {
            appActive.value = true
            start()

            send(waiting())

            played() shouldBe listOf("Glass.aiff")
            runner.commands.none { it.argv.first() == "/usr/bin/say" } shouldBe true
        }

    @Test
    fun `the voice switch off keeps the sound`() =
        runTest {
            start(settings = NotificationSettings(voice = false))

            send(waiting())

            played() shouldBe listOf("Glass.aiff")
        }

    @Test
    fun `one event gives say and afplay`() =
        runTest {
            start()

            send(waiting())

            runner.commands.map { it.argv.first() }.sorted() shouldBe listOf("/usr/bin/afplay", "/usr/bin/say")
        }

    @Test
    fun `two sounds within 2 s play one after the other`() =
        runTest {
            start()

            send(waiting())
            send(failed())
            played() shouldBe listOf("Glass.aiff")
            pass(2.seconds)

            played() shouldBe listOf("Glass.aiff", "Basso.aiff")
        }

    @Test
    fun `a muted session gets no sound`() =
        runTest {
            val center = start()
            send(waiting())
            val shown = played().size
            center.onAction(
                center.popups.value
                    .single()
                    .id,
                PopupAction.MUTE,
            )
            pass(5.seconds)

            send(waiting())

            played().size shouldBe shown
        }

    @Test
    fun `a missing sound file falls back to Glass`() =
        runTest {
            val picky =
                FakeProcessRunner {
                    val code = if ("Basso" in it.argv.last()) 1 else 0
                    Outcome.Ok(ProcessResult(code, "", ""))
                }
            start(process = picky)

            send(failed())

            played(picky) shouldBe listOf("Basso.aiff", "Glass.aiff")
        }

    @Test
    fun `a failing afplay does not block the next sound`() =
        runTest {
            val failing = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/afplay")) }
            start(process = failing)

            send(waiting())
            pass(2.seconds)
            send(failed())

            played(failing) shouldBe listOf("Glass.aiff", "Basso.aiff")
        }

    @Test
    fun `without a process runner nothing plays and the popups still work`() =
        runTest {
            val center = start(process = null)

            send(waiting())

            center.popups.value.size shouldBe 1
            runner.commands shouldBe emptyList()
        }
}
