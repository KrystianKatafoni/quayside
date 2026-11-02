package dev.quayside.notifications.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test. */
private const val WAIT_MS = 5_000L

/** Every checkbox of the matrix in tab order: row by row (kind), column by column (channel). */
private val MATRIX_ORDER =
    NotificationKind.entries.flatMap { kind -> NotificationChannel.entries.map { kind to it } }

/**
 * Scenarios of the notification settings dialog (FR-N1, FR-N2, FR-N3, NFR-6) on a fake repo. The dialog is a focusable
 * popup, so keys reach its controls and not the window behind it.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsDialogScreenTest {
    private val repo = FakeNotificationSettingsRepo()

    /** Shows the dialog over [repo] and opens it, the way the palette command does. */
    private fun ComposeUiTest.showDialog() {
        val built = mutableListOf<NotificationSettingsController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    NotificationSettingsController(repo, FakeVoiceCatalog(emptyList()), scope).also(built::add)
                }
            NotificationSettingsDialog(controller)
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        await(SettingsTags.DIALOG)
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    private fun cell(pair: Pair<NotificationKind, NotificationChannel>): String =
        SettingsTags.cell(pair.first, pair.second)

    /** A click gives the focus to the checkbox (and toggles it once); the dialog scrolls to it first. */
    private fun ComposeUiTest.clickCell(pair: Pair<NotificationKind, NotificationChannel>) {
        await(cell(pair)).performScrollTo().performClick()
        awaitFocus(cell(pair))
    }

    @Test
    fun `focus is on the popups switch when the dialog opens`() =
        runComposeUiTest {
            // When the dialog opens
            showDialog()

            // Then the "Popupy" switch has the focus (the effect of GlobalSwitches)
            awaitFocus(SettingsTags.POPUPS)
        }

    @Test
    fun `tab reaches every checkbox of the matrix`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the first checkbox of the matrix
            showDialog()
            clickCell(MATRIX_ORDER.first())

            // When the user presses Tab, once per checkbox
            MATRIX_ORDER.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(cell(from)).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next checkbox, in the order of the matrix
                awaitFocus(cell(to))
            }
        }

    @Test
    fun `Space toggles a channel`() =
        runComposeUiTest {
            // Given the open dialog; the focus is on TURN_ENDED - VOICE (a click toggled it on)
            showDialog()
            val voice = NotificationKind.TURN_ENDED to NotificationChannel.VOICE
            val sound = NotificationKind.TURN_ENDED to NotificationChannel.SOUND
            clickCell(voice)
            onNodeWithTag(cell(voice)).performKeyInput { pressKey(Key.Tab) }
            awaitFocus(cell(sound))

            // When the user presses Space on the next checkbox and saves
            onNodeWithTag(cell(sound)).performKeyInput { pressKey(Key.Spacebar) }
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then both channels are saved on top of the default popup
            waitUntil(timeoutMillis = WAIT_MS) {
                repo.settings.value.matrix
                    .isNotEmpty()
            }
            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe
                setOf(NotificationChannel.POPUP, NotificationChannel.VOICE, NotificationChannel.SOUND)
        }

    @Test
    fun `Escape closes the dialog`() =
        runComposeUiTest {
            // Given the open dialog with the focus on a checkbox
            showDialog()
            clickCell(MATRIX_ORDER.first())

            // When the user presses Escape
            onNodeWithTag(cell(MATRIX_ORDER.first())).performKeyInput { pressKey(Key.Escape) }

            // Then the dialog is gone and nothing is saved
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodesWithTag(SettingsTags.DIALOG).fetchSemanticsNodes().isEmpty()
            }
            repo.settings.value.matrix shouldBe emptyMap()
        }

    @Test
    fun `checkbox has an accessibility label`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // Then every checkbox of the matrix is described by its kind and channel
            MATRIX_ORDER.forEach { (kind, channel) ->
                await(SettingsTags.cell(kind, channel))
                    .assertContentDescriptionEquals(SettingsStrings.cellLabel(kind, channel))
            }
        }

    @Test
    fun `threshold out of range shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types a threshold out of range and saves
            await(SettingsTags.RUNNING_LONG_MINUTES).performScrollTo().performTextReplacement("0")
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then the message sits with the field and the dialog is still open
            val field = InvalidField(NotificationField.RUNNING_LONG_MINUTES)
            waitUntilExactlyOneExists(
                hasTestTag(SettingsTags.invalid(field.field)) and hasText(SettingsStrings.of(field)),
                WAIT_MS,
            )
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
        }

    @Test
    fun `switching popups off and saving reaches the repo`() =
        runComposeUiTest {
            // Given the open dialog with popups on
            showDialog()
            repo.settings.value.popups shouldBe true

            // When the user clicks the popups switch and saves
            await(SettingsTags.POPUPS).performScrollTo().performClick()
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then popups are off in the repo and the dialog is gone
            waitUntil(timeoutMillis = WAIT_MS) { !repo.settings.value.popups }
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodesWithTag(SettingsTags.DIALOG).fetchSemanticsNodes().isEmpty()
            }
        }

    private fun ComposeUiTest.assertPosition(position: PopupPosition) {
        await(SettingsTags.POPUP_POSITION).assertTextEquals(SettingsStrings.option("position", position))
    }

    private fun ComposeUiTest.pressOnPosition(key: Key) {
        onNodeWithTag(SettingsTags.POPUP_POSITION).performKeyInput { pressKey(key) }
    }

    @Test
    fun `arrow keys move the popup position through 6 places`() =
        runComposeUiTest {
            // Given the open dialog with the default position at the bottom right
            showDialog()
            assertPosition(PopupPosition.BOTTOM_RIGHT)

            // When the user clicks the field (focus and one step forward, wrapping)
            await(SettingsTags.POPUP_POSITION).performScrollTo().performClick()
            awaitFocus(SettingsTags.POPUP_POSITION)

            // Then the position wrapped to the top left
            assertPosition(PopupPosition.TOP_LEFT)

            // When the user presses the right arrow 5 times, the position steps through the whole list
            PopupPosition.entries.drop(1).forEach { next ->
                pressOnPosition(Key.DirectionRight)
                assertPosition(next)
            }

            // And once more: it wraps to the first place
            pressOnPosition(Key.DirectionRight)
            assertPosition(PopupPosition.TOP_LEFT)

            // And the left arrow goes back (wrapping to the last place)
            pressOnPosition(Key.DirectionLeft)
            assertPosition(PopupPosition.BOTTOM_RIGHT)
        }

    @Test
    fun `tab moves through the popup section in order`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the threshold field
            showDialog()
            await(SettingsTags.RUNNING_LONG_MINUTES).performScrollTo().performClick()
            awaitFocus(SettingsTags.RUNNING_LONG_MINUTES)
            val order =
                listOf(
                    SettingsTags.RUNNING_LONG_MINUTES,
                    SettingsTags.POPUP_POSITION,
                    SettingsTags.POPUP_DURATION,
                    SettingsTags.MAX_VISIBLE,
                    SettingsTags.POPUP_STYLE,
                )

            // When the user presses Tab, once per field
            order.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next field: position, duration, max visible, style
                awaitFocus(to)
            }
        }

    @Test
    fun `max visible out of range shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types 11 popups at once and saves
            await(SettingsTags.MAX_VISIBLE).performScrollTo().performTextReplacement("11")
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then the message sits with the field, the dialog is open and nothing is saved
            val field = InvalidField(NotificationField.MAX_VISIBLE)
            waitUntilExactlyOneExists(
                hasTestTag(SettingsTags.invalid(field.field)) and hasText(SettingsStrings.of(field)),
                WAIT_MS,
            )
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
            repo.settings.value.popup.maxVisible shouldBe 3
        }

    @Test
    fun `popup choices are saved`() =
        runComposeUiTest {
            // Given the open dialog with the default duration and style
            showDialog()

            // When the user clicks the style and the duration (each click steps forward) and saves
            await(SettingsTags.POPUP_STYLE).performScrollTo().performClick()
            await(SettingsTags.POPUP_DURATION).performScrollTo().performClick()
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then the repo holds the new style and duration, and the position stays
            waitUntil(timeoutMillis = WAIT_MS) { repo.settings.value.popup.style == PopupStyle.COMPACT }
            repo.settings.value.popup.duration shouldBe PopupDuration.ALWAYS_TIMED
            repo.settings.value.popup.position shouldBe PopupPosition.BOTTOM_RIGHT
        }

    @Test
    fun `popup section stays editable with popups off`() =
        runComposeUiTest {
            // Given the open dialog with popups switched off
            showDialog()
            await(SettingsTags.POPUPS).performScrollTo().performClick()

            // When the user clicks the style and saves
            await(SettingsTags.POPUP_STYLE).performScrollTo().performClick()
            await(SettingsTags.SAVE).performScrollTo().performClick()

            // Then popups are off and the new style is saved
            waitUntil(timeoutMillis = WAIT_MS) { repo.settings.value.popup.style == PopupStyle.COMPACT }
            repo.settings.value.popups shouldBe false
        }
}
