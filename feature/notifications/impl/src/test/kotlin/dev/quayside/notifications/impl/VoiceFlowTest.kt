package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.QuietHours
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
import java.time.Instant
import java.time.ZoneId
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
    private val clock = TestClock()
    private var zone: ZoneId = WARSAW

    private fun TestScope.start(
        process: FakeProcessRunner? = runner,
        settings: FakeNotificationSettingsRepo = repo,
    ) = notificationCenterModule(
        settings = settings,
        scope = backgroundScope,
        clock = clock,
        zone = { zone },
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

    /** Only the calls of `say`: sounds start `afplay` through the same runner. */
    private fun FakeProcessRunner.says() = commands.filter { it.argv.first() == "/usr/bin/say" }

    private val sayCommands get() = runner.says()

    private val spoken get() = sayCommands.map { it.argv.last() }

    @Test
    fun `a session that waits is announced by say`() =
        runTest {
            start()

            send(waiting("opcua-connector"))

            sayCommands.single().argv.let {
                it.first() shouldBe "/usr/bin/say"
                it.last() shouldBe "[[volm 0.70]] Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź"
            }
            sayCommands.single().argv shouldBe
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

            sayCommands shouldBe emptyList()
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

            sayCommands.size shouldBe 1
        }

    @Test
    fun `the voice switch off means no say`() =
        runTest {
            start(settings = FakeNotificationSettingsRepo(NotificationSettings(voice = false)))

            send(waiting("a"))
            pass(30.seconds)

            sayCommands shouldBe emptyList()
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

            sayCommands
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

            failing.says().size shouldBe 2
        }

    @Test
    fun `without a process runner nothing is spoken and the popups still work`() =
        runTest {
            val center = start(process = null)

            send(waiting("a"))

            center.popups.value.size shouldBe 1
            sayCommands shouldBe emptyList()
        }

    private val nightOn = NotificationSettings(quietHours = QuietHours(enabled = true))

    @Test
    fun `enabled quiet hours silence the voice but keep the popup`() =
        runTest {
            // Given quiet hours 22:00-07:00 and 22:30 in Warsaw
            clock.setTo(Instant.parse("2026-11-03T21:30:00Z"))
            val center = start(settings = FakeNotificationSettingsRepo(nightOn))

            // When a session waits
            send(waiting("a"))
            pass(30.seconds)

            // Then the popup shows and say is not started
            center.popups.value.size shouldBe 1
            sayCommands shouldBe emptyList()

            // And at noon the same settings speak again
            clock.setTo(Instant.parse("2026-11-04T11:00:00Z"))
            send(waiting("b"))
            sayCommands.size shouldBe 1
        }

    @Test
    fun `the zone is read at every event`() =
        runTest {
            clock.setTo(Instant.parse("2026-11-03T21:30:00Z"))
            start(settings = FakeNotificationSettingsRepo(nightOn))
            send(waiting("a"))
            sayCommands shouldBe emptyList()

            zone = ZoneId.of("UTC")
            send(waiting("b"))

            sayCommands.size shouldBe 1
        }

    private val plantpulse = ProjectId("plantpulse-core")

    private fun inProject(
        entry: ProjectNotifications,
        base: NotificationSettings = NotificationSettings(),
    ) = FakeNotificationSettingsRepo(base.copy(projects = mapOf(plantpulse to entry)))

    private fun failed(name: String) =
        SessionEvent.Failed(
            aSession(id = "s-$name", name = name, project = plantpulse, state = SessionState.Failed),
        )

    @Test
    fun `muted project gets no popup and no voice`() =
        runTest {
            // Given a muted project
            val center = start(settings = inProject(ProjectNotifications(ProjectProfile.MUTED)))

            // When one of its sessions waits and another fails
            send(waiting("a"))
            send(failed("b"))
            pass(30.seconds)

            // Then there is no popup and say is not started
            center.popups.value shouldBe emptyList()
            sayCommands shouldBe emptyList()
        }

    @Test
    fun `another project is not muted by the entry of a muted project`() =
        runTest {
            val center = start(settings = inProject(ProjectNotifications(ProjectProfile.MUTED)))

            send(
                SessionEvent.BecameBlocked(
                    aSession(
                        id = "x",
                        name = "x",
                        project = ProjectId("other"),
                        state = SessionState.AwaitingInput(null),
                    ),
                ),
            )

            center.popups.value.size shouldBe 1
            sayCommands.size shouldBe 1
        }

    @Test
    fun `errors only project is silent for a question and speaks a failure`() =
        runTest {
            // Given an errors only project
            val center = start(settings = inProject(ProjectNotifications(ProjectProfile.ERRORS_ONLY)))

            // When a session waits
            send(waiting("a"))

            // Then nothing happens
            center.popups.value shouldBe emptyList()
            sayCommands shouldBe emptyList()

            // And when a session fails, the popup shows and say speaks
            send(failed("b"))
            center.popups.value.size shouldBe 1
            sayCommands.size shouldBe 1
        }

    @Test
    fun `project voice reaches say argv`() =
        runTest {
            // Given a project with its own voice and a global voice Zosia
            start(settings = inProject(ProjectNotifications(voice = VoiceId("Anna"))))

            // When one of its sessions waits
            send(waiting("a"))

            // Then say is started with -v Anna and the global rate
            sayCommands
                .single()
                .argv
                .drop(1)
                .take(4) shouldBe listOf("-v", "Anna", "-r", "190")
        }

    @Test
    fun `a group of two projects is spoken with the global voice`() =
        runTest {
            start(
                settings =
                    FakeNotificationSettingsRepo(
                        NotificationSettings(
                            projects =
                                mapOf(
                                    plantpulse to ProjectNotifications(voice = VoiceId("Anna")),
                                    ProjectId("other") to ProjectNotifications(voice = VoiceId("Ewa")),
                                ),
                        ),
                    ),
            )
            send(waiting("a"))
            send(waiting("b"))
            send(
                SessionEvent.BecameBlocked(
                    aSession(
                        id = "x",
                        name = "x",
                        project = ProjectId("other"),
                        state = SessionState.AwaitingInput(null),
                    ),
                ),
            )
            pass(20.seconds)

            sayCommands.map { it.argv[2] } shouldBe listOf("Anna", "Zosia")
            spoken.last() shouldBe "[[volm 0.70]] 2 agenty czekają"
        }
}
