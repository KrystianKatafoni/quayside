package dev.quayside.notifications.impl

import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/** A question popup follows the snapshot of the session monitor through [notificationCenterModule] (#141). */
@OptIn(ExperimentalCoroutinesApi::class)
class AnsweredPopupFlowTest {
    private val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private val snapshot = MutableStateFlow(SessionsSnapshot.Initial)
    private val waiting = aSession(state = SessionState.AwaitingInput("Which branch?"))

    private fun fresh(
        second: Long,
        vararg sessions: Session,
    ) = SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(Instant.EPOCH.plusSeconds(second)))

    private fun TestScope.center(): NotificationCenter =
        notificationCenterModule(
            settings = FakeNotificationSettingsRepo(),
            scope = backgroundScope,
            clock = TestClock(),
            zone = { WARSAW },
            events = events,
            snapshot = snapshot,
        )

    private suspend fun TestScope.blocked(center: NotificationCenter) {
        snapshot.value = fresh(0, waiting)
        events.emit(SessionEvent.BecameBlocked(waiting))
        runCurrent()
        center.popups.value.size shouldBe 1
    }

    @Test
    fun `FR-N3 a question popup leaves when the session is answered in its terminal`() =
        runTest {
            val center = center()
            blocked(center)

            snapshot.value = fresh(3, aSession(state = SessionState.Working))
            runCurrent()

            center.popups.value shouldBe emptyList()
        }

    @Test
    fun `a question popup stays while the snapshot still shows the question`() =
        runTest {
            val center = center()
            blocked(center)

            snapshot.value = fresh(3, waiting)
            runCurrent()

            center.popups.value.size shouldBe 1
        }
}
