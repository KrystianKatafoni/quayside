package dev.quayside.app

import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.ui.NotificationSettingsController
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Ustawienia powiadomień" and its guard against open dialogs (FR-N1, AM-704). */
class NotificationSettingsCommandsTest {
    private fun TestScope.settings() =
        NotificationSettingsController(
            FakeNotificationSettingsRepo(),
            FakeVoiceCatalog(emptyList()),
            FakeVoicePreview(),
            backgroundScope,
        )

    @Test
    fun `offers notification settings when no dialog is open`() =
        runTest {
            val commands = notificationSettingsCommands(dialogOpen = false, settings())

            val command = commands.single()
            command.id shouldBe CommandId("notifications.settings")
            command.title shouldBe Strings["command.notificationSettings"]
            command.group shouldBe CommandGroup.APP
        }

    @Test
    fun `hidden while another dialog is open`() =
        runTest {
            notificationSettingsCommands(dialogOpen = true, settings()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the dialog`() =
        runTest {
            val settings = settings()
            settings.state.value.shouldBeNull()

            notificationSettingsCommands(dialogOpen = false, settings).single().run()

            settings.state.value.shouldNotBeNull()
        }
}
