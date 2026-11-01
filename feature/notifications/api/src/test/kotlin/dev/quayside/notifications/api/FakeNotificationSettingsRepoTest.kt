package dev.quayside.notifications.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The fake passes the same contract as the real repository (ENG-T2). */
class FakeNotificationSettingsRepoTest : NotificationSettingsRepoContract() {
    override fun create(): NotificationSettingsRepo = FakeNotificationSettingsRepo()

    @Test
    fun `fake starts from the given settings`() {
        val initial = NotificationSettings(popups = false)

        FakeNotificationSettingsRepo(initial).settings.value shouldBe initial
    }
}
