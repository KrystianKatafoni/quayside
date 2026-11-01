package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationDelivery
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupAction
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [DefaultNotificationCenter]: bells and session events become deliveries and popups (AM-509, AM-702). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultNotificationCenterTest {
    private val clock = TestClock()
    private val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private val attached = mutableListOf<SessionId>()

    private fun TestScope.center(
        repo: FakeNotificationSettingsRepo = FakeNotificationSettingsRepo(),
    ): NotificationCenter =
        notificationCenterModule(
            settings = repo,
            scope = backgroundScope,
            clock = clock,
            events = events,
            projectName = { id: ProjectId? -> id?.let { "Quayside" } },
            onAttach = { attached += it },
        )

    /** Emits [event] and lets the collector of the center handle it. */
    private suspend fun TestScope.send(event: SessionEvent) {
        events.emit(event)
        runCurrent()
    }

    private fun TestScope.collected(center: NotificationCenter): List<NotificationDelivery> {
        val seen = mutableListOf<NotificationDelivery>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.deliveries.toList(seen) }
        return seen
    }

    private val waiting = aSession(state = SessionState.AwaitingInput("Which branch?"))

    @Test
    fun `a bell is delivered as a popup without a session`() =
        runTest {
            val center = center()
            val seen = collected(center)

            center.notifyTerminalBell()

            seen shouldBe
                listOf(NotificationDelivery(NotificationChannel.POPUP, NotificationKind.TERMINAL_BELL, null))
            center.popups.value shouldHaveSize 1
            center.popups.value
                .single()
                .session shouldBe null
        }

    @Test
    fun `the settings of the moment decide`() =
        runTest {
            val repo = FakeNotificationSettingsRepo()
            val center = center(repo)
            val seen = collected(center)
            repo.update { it.copy(popups = false) }
            center.notifyTerminalBell()
            repo.update { NotificationSettings() }

            center.notifyTerminalBell()

            seen.size shouldBe 1
        }

    @Test
    fun `a bell with no collector is lost and does not block`() =
        runTest {
            val center = center()
            center.notifyTerminalBell()

            val seen = collected(center)

            seen shouldBe emptyList()
        }

    @Test
    fun `FR-N3 a session that waits shows a popup with project, name and question`() =
        runTest {
            val center = center()

            send(SessionEvent.BecameBlocked(waiting))

            val popup = center.popups.value.single()
            popup.kind shouldBe NotificationKind.NEEDS_INPUT
            popup.session shouldBe waiting.id
            popup.project shouldBe "Quayside"
            popup.sessionName shouldBe "fix-login"
            popup.detail shouldBe "Which branch?"
            popup.sticky shouldBe true
        }

    @Test
    fun `a finished turn shows a timed popup`() =
        runTest {
            val center = center()

            send(SessionEvent.TurnFinished(aSession(state = SessionState.Done)))

            center.popups.value
                .single()
                .sticky shouldBe false
        }

    @Test
    fun `an event whose kind has no popup channel shows nothing`() =
        runTest {
            val center = center(FakeNotificationSettingsRepo(NotificationSettings(popups = false)))

            send(SessionEvent.BecameBlocked(waiting))

            center.popups.value shouldBe emptyList()
        }

    @Test
    fun `FR-N3 mute silences every channel of the session and closes its popups`() =
        runTest {
            val center = center()
            val seen = collected(center)
            send(SessionEvent.BecameBlocked(waiting))
            val shown = seen.size

            center.onAction(
                center.popups.value
                    .single()
                    .id,
                PopupAction.MUTE,
            )
            send(SessionEvent.BecameBlocked(waiting))

            center.popups.value shouldBe emptyList()
            seen.size shouldBe shown
        }

    @Test
    fun `FR-N3 attach opens the session and closes the popup`() =
        runTest {
            val center = center()
            send(SessionEvent.BecameBlocked(waiting))

            center.onAction(
                center.popups.value
                    .single()
                    .id,
                PopupAction.ATTACH,
            )

            attached shouldBe listOf(waiting.id)
            center.popups.value shouldBe emptyList()
        }
}
