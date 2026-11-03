package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationDelivery
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
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
import kotlin.time.Duration.Companion.minutes

/** [DefaultNotificationCenter] with project exceptions (FR-N7) and the 15 minute mute of a session (FR-N3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectProfileCenterTest {
    private val clock = TestClock()
    private val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)

    private fun repoWith(profile: ProjectProfile) =
        FakeNotificationSettingsRepo(
            NotificationSettings(projects = mapOf(PROJECT to ProjectNotifications(profile))),
        )

    private fun TestScope.center(repo: FakeNotificationSettingsRepo): NotificationCenter =
        notificationCenterModule(
            settings = repo,
            scope = backgroundScope,
            clock = clock,
            zone = { WARSAW },
            events = events,
            projectName = { id: ProjectId? -> id?.value },
        )

    private fun TestScope.collected(center: NotificationCenter): List<NotificationDelivery> {
        val seen = mutableListOf<NotificationDelivery>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.deliveries.toList(seen) }
        return seen
    }

    private suspend fun TestScope.send(event: SessionEvent) {
        events.emit(event)
        runCurrent()
    }

    private val waiting =
        SessionEvent.BecameBlocked(
            aSession(project = PROJECT, state = SessionState.AwaitingInput(null)),
        )
    private val failed = SessionEvent.Failed(aSession(project = PROJECT, state = SessionState.Failed))

    @Test
    fun `a muted project shows no popup and delivers only the badge`() =
        runTest {
            val center = center(repoWith(ProjectProfile.MUTED))
            val seen = collected(center)

            send(waiting)

            center.popups.value shouldBe emptyList()
            seen shouldBe
                listOf(
                    NotificationDelivery(
                        NotificationChannel.DOCK_BADGE,
                        NotificationKind.NEEDS_INPUT,
                        waiting.session.id,
                    ),
                )
        }

    @Test
    fun `an errors only project shows a failure and ignores a question`() =
        runTest {
            val center = center(repoWith(ProjectProfile.ERRORS_ONLY))

            send(waiting)
            center.popups.value shouldBe emptyList()
            send(failed)

            center.popups.value shouldHaveSize 1
            center.popups.value
                .single()
                .kind shouldBe NotificationKind.FAILED
        }

    @Test
    fun `the settings of the moment decide the profile`() =
        runTest {
            val repo = repoWith(ProjectProfile.MUTED)
            val center = center(repo)
            send(waiting)
            center.popups.value shouldBe emptyList()

            repo.update { it.copy(projects = emptyMap()) }
            send(waiting)

            center.popups.value shouldHaveSize 1
        }

    @Test
    fun `a session muted for 15 minutes stays muted in an errors only project and returns after them`() =
        runTest {
            val center = center(repoWith(ProjectProfile.ERRORS_ONLY))
            send(failed)
            center.onAction(
                center.popups.value
                    .single()
                    .id,
                PopupAction.MUTE,
            )

            send(failed)
            center.popups.value shouldBe emptyList()
            clock.advanceBy(14.minutes)
            send(failed)
            center.popups.value shouldBe emptyList()
            clock.advanceBy(1.minutes)
            send(failed)

            center.popups.value shouldHaveSize 1
        }

    private companion object {
        val PROJECT = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
    }
}
