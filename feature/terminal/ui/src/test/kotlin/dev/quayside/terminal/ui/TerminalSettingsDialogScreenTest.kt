package dev.quayside.terminal.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test; see TerminalAreaScreenTest. */
private const val WAIT_MS = 5_000L

/** Every control of the dialog in tab order (NFR-6: the dialog works with the keyboard alone). */
private val TAB_ORDER =
    listOf(
        TerminalTags.SETTINGS_SHELL,
        TerminalTags.SETTINGS_START_DIRECTORY,
        TerminalTags.SETTINGS_FONT_FAMILY,
        TerminalTags.SETTINGS_FONT_SIZE,
        TerminalTags.SETTINGS_SCROLLBACK,
        TerminalTags.SETTINGS_LIGATURES,
        TerminalTags.SETTINGS_OPTION_AS_META,
        TerminalTags.SETTINGS_BELL,
        TerminalTags.SETTINGS_SAVE,
        TerminalTags.SETTINGS_CANCEL,
    )

/**
 * Scenarios of the settings dialog (FR-T7, NFR-6) on a fake repo. The dialog is a focusable popup, so keys reach
 * its controls and not the window behind it.
 */
@OptIn(ExperimentalTestApi::class)
class TerminalSettingsDialogScreenTest {
    /** Shows the dialog over [repo] and opens it, the way the palette command does. */
    private fun ComposeUiTest.showDialog(repo: FakeTerminalSettingsRepo = FakeTerminalSettingsRepo()) {
        val built = mutableListOf<TerminalSettingsController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { TerminalSettingsController(repo, scope).also { built += it } }
            TerminalSettingsDialog(controller)
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        await(TerminalTags.SETTINGS_DIALOG)
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    @Test
    fun `NFR-6 tab moves through every field`() =
        runComposeUiTest {
            // Given the open dialog; the focus starts on the first field
            showDialog()
            awaitFocus(TAB_ORDER.first())

            // When the user presses Tab, once per control
            TAB_ORDER.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next control, in the order of the screen
                awaitFocus(to)
            }

            // When the user presses Shift Tab on the last control
            onNodeWithTag(TAB_ORDER.last()).performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }

            // Then the focus goes back one control
            awaitFocus(TAB_ORDER[TAB_ORDER.size - 2])
        }

    @Test
    fun `FR-T7 refused field shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types a font size out of range and saves
            await(TerminalTags.SETTINGS_FONT_SIZE).performTextReplacement("7")
            await(TerminalTags.SETTINGS_SAVE).performClick()

            // Then the message sits with the font size and the dialog is still open
            val field = TerminalSettingsField.FONT_SIZE
            waitUntilExactlyOneExists(
                hasTestTag(TerminalTags.settingsInvalid(field)) and hasText(TerminalStrings.of(field)),
                WAIT_MS,
            )
            onNodeWithTag(TerminalTags.SETTINGS_DIALOG).assertExists()
        }

    @Test
    fun `FR-T7 storage failure shows a message and the dialog stays`() =
        runComposeUiTest {
            // Given a repo that cannot write and the open dialog
            showDialog(FakeTerminalSettingsRepo().apply { failure = true })

            // When the user saves
            await(TerminalTags.SETTINGS_SAVE).performClick()

            // Then the fixed storage message shows and the dialog is still open
            await(TerminalTags.SETTINGS_STORAGE_ERROR)
            onNodeWithTag(TerminalTags.SETTINGS_DIALOG).assertExists()
        }

    @Test
    fun `FR-T7 recovered file shows the notice`() =
        runComposeUiTest {
            // Given a settings file that was broken at start
            val issue = TerminalSettingsIssue.RECOVERED_FROM_BROKEN_FILE

            // When the user opens the dialog
            showDialog(FakeTerminalSettingsRepo(startIssue = issue))

            // Then the notice shows its text
            waitUntilExactlyOneExists(
                hasTestTag(TerminalTags.SETTINGS_NOTICE) and hasText(TerminalStrings.of(issue)),
                WAIT_MS,
            )
        }

    @Test
    fun `NFR-6 Escape closes the dialog`() =
        runComposeUiTest {
            // Given the open dialog with the focus on its first field
            showDialog()
            awaitFocus(TerminalTags.SETTINGS_SHELL)

            // When the user presses Escape
            onNodeWithTag(TerminalTags.SETTINGS_SHELL).performKeyInput { pressKey(Key.Escape) }

            // Then the dialog is gone
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodesWithTag(TerminalTags.SETTINGS_DIALOG).fetchSemanticsNodes().isEmpty()
            }
        }

    @Test
    fun `NFR-6 arrow keys and Space change a choice`() =
        runComposeUiTest {
            // Given the open dialog; the shell shows the first profile
            showDialog()
            awaitFocus(TerminalTags.SETTINGS_SHELL)
            val field = onNodeWithTag(TerminalTags.SETTINGS_SHELL)
            val shows = { index: Int ->
                hasTestTag(TerminalTags.SETTINGS_SHELL) and
                    hasText(TerminalStrings.of(ShellProfile.entries[index]), substring = true)
            }
            waitUntilExactlyOneExists(shows(0), WAIT_MS)

            // When the user presses Right
            field.performKeyInput { pressKey(Key.DirectionRight) }

            // Then the next profile shows
            waitUntilExactlyOneExists(shows(1), WAIT_MS)

            // When the user presses Left, then Space
            field.performKeyInput { pressKey(Key.DirectionLeft) }
            waitUntilExactlyOneExists(shows(0), WAIT_MS)
            field.performKeyInput { pressKey(Key.Spacebar) }

            // Then Space goes to the next profile again
            waitUntilExactlyOneExists(shows(1), WAIT_MS)
        }

    @Test
    fun `NFR-6 Space toggles a checkbox`() =
        runComposeUiTest {
            // Given the open dialog; a click toggles ligatures once and leaves the focus on them
            val repo = FakeTerminalSettingsRepo()
            showDialog(repo)
            val before = repo.settings.value.ligatures
            await(TerminalTags.SETTINGS_LIGATURES).performClick()
            awaitFocus(TerminalTags.SETTINGS_LIGATURES)

            // When the user presses Space on the checkbox and saves
            onNodeWithTag(TerminalTags.SETTINGS_LIGATURES).performKeyInput { pressKey(Key.Spacebar) }
            await(TerminalTags.SETTINGS_SAVE).performClick()

            // Then the saved value is the original again: the click and the Space each toggled once
            waitUntil(timeoutMillis = WAIT_MS) { repo.saves == 1 }
            repo.settings.value.ligatures shouldBe before
        }
}
