package dev.quayside.notifications.impl

import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Timers, mutes and the stack of [PopupManager] on virtual time (FR-N3). */
@OptIn(ExperimentalCoroutinesApi::class)
class PopupManagerTest {
    private val settings = MutableStateFlow(NotificationSettings())
    private val clock = TestClock()

    private fun TestScope.manager() = PopupManager(settings, backgroundScope, clock) {}

    private fun delivery(
        kind: NotificationKind,
        id: String = "7c5dcf5d",
        state: SessionState = SessionState.Done,
    ) = Delivery(NotificationChannel.POPUP, kind, aSession(id = id, state = state))

    private fun snapshotOf(
        vararg sessions: Session,
        status: SnapshotStatus = SnapshotStatus.Fresh(Instant.EPOCH),
    ) = SessionsSnapshot(sessions.toList(), status)

    private val asking = SessionState.AwaitingInput("Which branch?")
    private val permission = SessionState.AwaitingPermission("Bash")

    @Test
    fun `a timed popup leaves after the timeout`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.TURN_ENDED), "Quayside")

            advanceTimeBy(PopupManager.TIMEOUT - 1.seconds)
            manager.popups.value.size shouldBe 1
            advanceTimeBy(2.seconds)

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `a sticky popup stays`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT), "Quayside")

            advanceTimeBy(10.minutes)

            manager.popups.value.size shouldBe 1
        }

    @Test
    fun `always-timed makes questions leave too`() =
        runTest {
            settings.value = NotificationSettings(popup = PopupSettings(duration = PopupDuration.ALWAYS_TIMED))
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT), "Quayside")

            advanceTimeBy(PopupManager.TIMEOUT + 1.seconds)

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `dismissing a popup cancels its timer and a later popup is not removed by it`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.TURN_ENDED, id = "a"), null)
            manager.act(
                manager.popups.value
                    .single()
                    .id,
                PopupAction.DISMISS,
            )
            advanceTimeBy(5.seconds)
            manager.show(delivery(NotificationKind.TURN_ENDED, id = "b"), null)

            advanceTimeBy(5.seconds)

            manager.popups.value.size shouldBe 1
        }

    @Test
    fun `a collapsed popup expands when one above it is dismissed`() =
        runTest {
            settings.value = NotificationSettings(popup = PopupSettings(maxVisible = 1))
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "a"), null)
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "b"), null)
            manager.popups.value.map { it.collapsed } shouldBe listOf(false, true)

            manager.act(
                manager.popups.value
                    .first()
                    .id,
                PopupAction.DISMISS,
            )

            manager.popups.value.map { it.collapsed } shouldBe listOf(false)
        }

    @Test
    fun `mute lasts fifteen minutes`() =
        runTest {
            val manager = manager()
            val session = aSession().id
            manager.show(delivery(NotificationKind.NEEDS_INPUT), null)

            manager.act(
                manager.popups.value
                    .single()
                    .id,
                PopupAction.MUTE,
            )

            manager.isMuted(session) shouldBe true
            clock.advanceBy(PopupManager.MUTE - 1.seconds)
            manager.isMuted(session) shouldBe true
            clock.advanceBy(2.seconds)
            manager.isMuted(session) shouldBe false
        }

    @Test
    fun `mute closes only the popups of that session`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "a"), null)
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "b"), null)

            manager.act(
                manager.popups.value
                    .first { it.session?.value == "a" }
                    .id,
                PopupAction.MUTE,
            )

            manager.popups.value.map { it.session?.value } shouldBe listOf("b")
        }

    @Test
    fun `an action on an unknown popup is ignored`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT), null)

            manager.act(PopupId("nope"), PopupAction.MUTE)

            manager.popups.value.size shouldBe 1
        }

    @Test
    fun `a session without a mute is not muted and no session is never muted`() =
        runTest {
            val manager = manager()

            manager.isMuted(aSession().id) shouldBe false
            manager.isMuted(null) shouldBe false
        }

    @Test
    fun `FR-N3 a question popup leaves when its session works again`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, state = asking), "Quayside")

            manager.retireAnswered { snapshotOf(aSession(state = SessionState.Working)) }

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `FR-N3 a permission popup leaves when its session finished the turn`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.PERMISSION, state = permission), "Quayside")

            manager.retireAnswered { snapshotOf(aSession(state = SessionState.Done)) }

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `a question popup stays while its session still waits for the same thing`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, state = asking), "Quayside")
            manager.show(delivery(NotificationKind.PERMISSION, id = "b", state = permission), "Quayside")

            manager.retireAnswered {
                snapshotOf(aSession(state = asking), aSession(id = "b", state = permission))
            }

            manager.popups.value.size shouldBe 2
        }

    @Test
    fun `a permission popup leaves when the session now asks a question instead`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.PERMISSION, state = permission), "Quayside")

            manager.retireAnswered { snapshotOf(aSession(state = asking)) }

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `a question popup leaves when its session is no longer listed`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, state = asking), "Quayside")

            manager.retireAnswered { snapshotOf(aSession(id = "other", state = asking)) }

            manager.popups.value shouldBe emptyList()
        }

    @Test
    fun `only the popup of the session that moved on leaves`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "a", state = asking), "Quayside")
            manager.show(delivery(NotificationKind.NEEDS_INPUT, id = "b", state = asking), "Quayside")

            manager.retireAnswered {
                snapshotOf(aSession(id = "a", state = SessionState.Working), aSession(id = "b", state = asking))
            }

            manager.popups.value
                .map { it.session?.value }
                .shouldBe(listOf("b"))
        }

    @Test
    fun `popups that are not questions stay even when the session moved on`() =
        runTest {
            settings.value = NotificationSettings(popup = PopupSettings(duration = PopupDuration.ALWAYS_STAY))
            val manager = manager()
            manager.show(delivery(NotificationKind.TURN_ENDED), "Quayside")
            manager.show(delivery(NotificationKind.FAILED, id = "b", state = SessionState.Failed), "Quayside")

            manager.retireAnswered { snapshotOf(aSession(state = SessionState.Working)) }

            manager.popups.value.size shouldBe 2
        }

    @Test
    fun `a snapshot that was not listed yet retires nothing`() =
        runTest {
            val manager = manager()
            manager.show(delivery(NotificationKind.NEEDS_INPUT, state = asking), "Quayside")

            manager.retireAnswered { SessionsSnapshot.Initial }

            manager.popups.value.size shouldBe 1
        }
}
