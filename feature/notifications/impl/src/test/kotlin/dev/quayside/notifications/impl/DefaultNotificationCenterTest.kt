package dev.quayside.notifications.impl

import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationDelivery
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [DefaultNotificationCenter]: the bell becomes deliveries under the settings of the moment (AM-509). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultNotificationCenterTest {
    private val repo = FakeNotificationSettingsRepo()
    private val center = notificationCenterModule(repo)

    private fun TestScope.collected(): List<NotificationDelivery> {
        val seen = mutableListOf<NotificationDelivery>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.deliveries.toList(seen) }
        return seen
    }

    @Test
    fun `a bell is delivered as a popup without a session`() =
        runTest {
            val seen = collected()

            center.notifyTerminalBell()

            seen shouldBe
                listOf(NotificationDelivery(NotificationChannel.POPUP, NotificationKind.TERMINAL_BELL, null))
        }

    @Test
    fun `the settings of the moment decide`() =
        runTest {
            val seen = collected()
            repo.update { it.copy(popups = false) }
            center.notifyTerminalBell()
            repo.update { NotificationSettings() }

            center.notifyTerminalBell()

            seen.size shouldBe 1
        }

    @Test
    fun `a bell with no collector is lost and does not block`() =
        runTest {
            center.notifyTerminalBell()

            val seen = collected()

            seen shouldBe emptyList()
        }
}
