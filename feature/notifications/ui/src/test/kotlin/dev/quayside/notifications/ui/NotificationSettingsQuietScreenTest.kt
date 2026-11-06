package dev.quayside.notifications.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.QuietHours
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalTime

private const val WAIT_MS = 5_000L

/** Tab order from the last speech switch to Save when no project is registered (plan AM-706). */
private val QUIET_ORDER =
    listOf(
        SettingsTags.MUTE_MICROPHONE,
        SettingsTags.QUIET_ENABLED,
        SettingsTags.QUIET_FROM,
        SettingsTags.QUIET_TO,
        SettingsTags.RESPECT_FOCUS,
        SettingsTags.SAVE,
    )

/** Scenarios of the "Nie przeszkadzać" section of the notification settings dialog (FR-N6, NFR-6). */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsQuietScreenTest {
    private var repo = FakeNotificationSettingsRepo()

    private fun ComposeUiTest.showDialog(saved: NotificationSettings = NotificationSettings()) {
        repo = FakeNotificationSettingsRepo(saved)
        showDialogOver(repo)
    }

    @Test
    fun `quiet hours switch and times are saved`() =
        runComposeUiTest {
            // Given the open dialog with the default (off) quiet hours
            showDialog()
            repo.settings.value.quietHours shouldBe QuietHours()

            // When the user switches the hours on, types 23:00 and 06:30 and saves
            awaitTag(SettingsTags.QUIET_ENABLED).performScrollTo().performClick()
            awaitTag(SettingsTags.QUIET_FROM).performScrollTo().performTextReplacement("23:00")
            awaitTag(SettingsTags.QUIET_TO).performScrollTo().performTextReplacement("06:30")
            clickSave()
            awaitDialogClosed()

            // Then the repo holds the hours at once; the Focus switch was not touched
            repo.settings.value.quietHours shouldBe
                QuietHours(enabled = true, from = LocalTime.of(23, 0), to = LocalTime.of(6, 30), respectFocus = true)
        }

    @Test
    fun `the fields show the saved hours`() =
        runComposeUiTest {
            // Given saved hours 21:30 - 06:15
            showDialog(NotificationSettings(quietHours = QuietHours(true, LocalTime.of(21, 30), LocalTime.of(6, 15))))

            // Then the fields hold them as HH:MM
            awaitTag(SettingsTags.QUIET_FROM).performScrollTo()
            waitUntilExactlyOneExists(hasTestTag(SettingsTags.QUIET_FROM) and hasText("21:30"), WAIT_MS)
            waitUntilExactlyOneExists(hasTestTag(SettingsTags.QUIET_TO) and hasText("06:15"), WAIT_MS)
        }

    @Test
    fun `wrong time shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types an hour that does not exist and saves
            awaitTag(SettingsTags.QUIET_FROM).performScrollTo().performTextReplacement("25:00")
            clickSave()

            // Then the message sits with the field, the dialog is open and nothing is saved
            val field = InvalidField(NotificationField.QUIET_FROM)
            waitUntilExactlyOneExists(
                hasTestTag(SettingsTags.invalid(field.field)) and hasText(SettingsStrings.of(field)),
                WAIT_MS,
            )
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
            repo.settings.value.quietHours shouldBe QuietHours()
        }

    @Test
    fun `equal times with the hours on show the message of the end`() =
        runComposeUiTest {
            // Given the hours switched on and the same time in both fields
            showDialog()
            awaitTag(SettingsTags.QUIET_ENABLED).performScrollTo().performClick()
            awaitTag(SettingsTags.QUIET_FROM).performScrollTo().performTextReplacement("08:00")
            awaitTag(SettingsTags.QUIET_TO).performScrollTo().performTextReplacement("08:00")

            // When the user saves
            clickSave()

            // Then the end is refused and the dialog stays
            val field = InvalidField(NotificationField.QUIET_TO)
            waitUntilExactlyOneExists(
                hasTestTag(SettingsTags.invalid(field.field)) and hasText(SettingsStrings.of(field)),
                WAIT_MS,
            )
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
        }

    @Test
    fun `focus switch shows the not yet working note and is saved`() =
        runComposeUiTest {
            // Given the open dialog; respecting the Focus is on by default
            showDialog()

            // Then the note says the feature does not work yet
            awaitTag(SettingsTags.FOCUS_NOTE).performScrollTo()
            awaitTagText(SettingsTags.FOCUS_NOTE, "Jeszcze nie działa")

            // When the user switches the Focus off and saves
            awaitTag(SettingsTags.RESPECT_FOCUS).performScrollTo().performClick()
            clickSave()
            awaitDialogClosed()

            // Then the repo holds it
            repo.settings.value.quietHours shouldBe QuietHours(respectFocus = false)
        }

    @Test
    fun `tab moves through the quiet section in order`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the last switch of the speech section
            showDialog()
            awaitTag(SettingsTags.MUTE_MICROPHONE).performScrollTo().focus()
            awaitFocusOn(SettingsTags.MUTE_MICROPHONE)

            // When the user presses Tab, once per control
            QUIET_ORDER.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next one: switch, from, to, Focus switch, Save
                awaitFocusOn(to)
            }
        }
}
