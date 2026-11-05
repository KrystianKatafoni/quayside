package dev.quayside.terminal.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
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
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import io.kotest.matchers.collections.shouldContain
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
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller = remember { TerminalSettingsController(repo, scope).also { built += it } }
                TerminalSettingsDialog(controller)
            }
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
    fun `NFR-6 notice is drawn in the warn token and storage error in the err token`() =
        runComposeUiTest {
            // Given a broken file at start and a repo that cannot write, in the plex theme
            val tokens = SampleTokens.colors()
            val issue = TerminalSettingsIssue.RECOVERED_FROM_BROKEN_FILE
            showDialog(FakeTerminalSettingsRepo(startIssue = issue).apply { failure = true })
            await(TerminalTags.SETTINGS_SAVE).performClick()
            await(TerminalTags.SETTINGS_STORAGE_ERROR)

            // When the notice and the storage error are captured
            fun colorsOf(tag: String): Set<Int> {
                val image = onNodeWithTag(tag).captureToImage().toPixelMap()
                return (0 until image.width).flatMapTo(
                    HashSet(),
                ) { x -> (0 until image.height).map { image[x, it].toArgb() } }
            }

            // Then the notice has the colour warn and the error the colour err
            colorsOf(TerminalTags.SETTINGS_NOTICE) shouldContain
                tokens.status.warn
                    .toColor()
                    .toArgb()
            colorsOf(TerminalTags.SETTINGS_STORAGE_ERROR) shouldContain
                tokens.status.err
                    .toColor()
                    .toArgb()
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

    /** Keys go to the open list, else to the shell field (a key sent to the dialog panel does not reach a field). */
    private fun ComposeUiTest.pressInDialog(key: Key) {
        val open = onAllNodesWithTag(AmDropdownTags.LIST).fetchSemanticsNodes().isNotEmpty()
        val target = if (open) AmDropdownTags.LIST else TerminalTags.SETTINGS_SHELL
        onNodeWithTag(target).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitListGone() {
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodesWithTag(AmDropdownTags.LIST).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun ComposeUiTest.shellShows(index: Int) =
        waitUntilExactlyOneExists(
            hasTestTag(TerminalTags.SETTINGS_SHELL) and
                hasText(TerminalStrings.of(ShellProfile.entries[index]), substring = true),
            WAIT_MS,
        )

    /** Opens the list of [field] with a click and clicks its option [index]. */
    private fun ComposeUiTest.pick(
        field: String,
        index: Int,
    ) {
        await(field).performClick()
        await(AmDropdownTags.option(index)).performClick()
        awaitListGone()
    }

    @Test
    fun `NFR-6 shell field has the focus when the dialog opens`() =
        runComposeUiTest {
            showDialog()

            awaitFocus(TerminalTags.SETTINGS_SHELL)
            onNodeWithTag(AmDropdownTags.LIST).assertDoesNotExist()
        }

    @Test
    fun `NFR-6 Enter opens a choice and arrow keys with Enter change it`() =
        runComposeUiTest {
            // Given the open dialog; the shell shows the first profile and has the focus
            showDialog()
            awaitFocus(TerminalTags.SETTINGS_SHELL)
            shellShows(0)

            // When the user presses Enter, the list opens on the first profile
            pressInDialog(Key.Enter)
            await(AmDropdownTags.LIST)
            ShellProfile.entries.indices.forEach { await(AmDropdownTags.option(it)) }
            awaitFocus(AmDropdownTags.option(0))

            // And Down then Enter picks the next profile; the focus goes back to the field
            pressInDialog(Key.DirectionDown)
            awaitFocus(AmDropdownTags.option(1))
            pressInDialog(Key.Enter)
            awaitListGone()
            shellShows(1)
            awaitFocus(TerminalTags.SETTINGS_SHELL)

            // And a side arrow on the closed field only opens the list; Escape closes it with no change
            pressInDialog(Key.DirectionRight)
            await(AmDropdownTags.LIST)
            pressInDialog(Key.Escape)
            awaitListGone()
            shellShows(1)

            // And Space opens the list again, Escape closes it with no change
            pressInDialog(Key.Spacebar)
            await(AmDropdownTags.LIST)
            pressInDialog(Key.Escape)
            awaitListGone()
            shellShows(1)
        }

    @Test
    fun `NFR-6 Escape in an open list closes only the list`() =
        runComposeUiTest {
            // Given the open dialog and the open list of the shell
            showDialog()
            awaitFocus(TerminalTags.SETTINGS_SHELL)
            pressInDialog(Key.Enter)
            await(AmDropdownTags.LIST)

            // When the user presses Escape
            pressInDialog(Key.Escape)

            // Then the list is gone, the dialog stays and the field has the focus
            awaitListGone()
            onNodeWithTag(TerminalTags.SETTINGS_DIALOG).assertExists()
            awaitFocus(TerminalTags.SETTINGS_SHELL)

            // And the next Escape cancels the dialog
            pressInDialog(Key.Escape)
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodesWithTag(TerminalTags.SETTINGS_DIALOG).fetchSemanticsNodes().isEmpty()
            }
        }

    @Test
    fun `FR-T7 shell and start directory are picked from lists and saved`() =
        runComposeUiTest {
            // Given the open dialog with the default shell and start directory
            val repo = FakeTerminalSettingsRepo()
            showDialog(repo)

            // When the user picks fish and the home directory by clicks and saves
            pick(TerminalTags.SETTINGS_SHELL, ShellProfile.entries.indexOf(ShellProfile.FISH))
            pick(TerminalTags.SETTINGS_START_DIRECTORY, StartDirectory.entries.indexOf(StartDirectory.HOME))
            await(TerminalTags.SETTINGS_SAVE).performClick()

            // Then the repo holds both
            waitUntil(timeoutMillis = WAIT_MS) { repo.saves == 1 }
            repo.settings.value.shell shouldBe ShellProfile.FISH
            repo.settings.value.startDirectory shouldBe StartDirectory.HOME
        }

    @Test
    fun `NFR-6 the list of the start directory offers its 3 places`() =
        runComposeUiTest {
            showDialog()

            await(TerminalTags.SETTINGS_START_DIRECTORY).performClick()

            StartDirectory.entries.forEachIndexed { index, directory ->
                waitUntilExactlyOneExists(
                    hasTestTag(AmDropdownTags.option(index)) and
                        hasText(TerminalStrings.of(directory), substring = true),
                    WAIT_MS,
                )
            }
            onNodeWithTag(AmDropdownTags.option(StartDirectory.entries.size)).assertDoesNotExist()
        }

    @Test
    fun `NFR-6 each choice has exactly its own name for the screen reader`() =
        runComposeUiTest {
            showDialog()

            await(TerminalTags.SETTINGS_SHELL).assertContentDescriptionEquals(TerminalStrings["settings.shell"])
            await(TerminalTags.SETTINGS_START_DIRECTORY)
                .assertContentDescriptionEquals(TerminalStrings["settings.startDirectory"])
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
