package dev.quayside.terminal.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/**
 * The focus frame of the buttons of the settings dialog (NFR-6, AM-806): `amFocusFrame` in the colour `text`, seen on
 * a capture of the whole dialog (the frame is drawn at the edge of the button, so the button alone is not a proof).
 */
@OptIn(ExperimentalTestApi::class)
class TerminalSettingsFocusFrameTest {
    private val frame =
        SampleTokens
            .colors()
            .content.text
            .toColor()
            .toArgb()

    private fun ComposeUiTest.showDialog() {
        val built = mutableListOf<TerminalSettingsController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember { TerminalSettingsController(FakeTerminalSettingsRepo(), scope).also { built += it } }
                TerminalSettingsDialog(controller)
            }
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        waitUntilExactlyOneExists(hasTestTag(TerminalTags.SETTINGS_DIALOG), WAIT_MS)
    }

    private fun ComposeUiTest.node(tag: String): SemanticsNodeInteraction = onNodeWithTag(tag)

    /** ARGB of the dialog pixel 1 px inside the left edge of the node [tag], at half of its height. */
    private fun ComposeUiTest.edgePixel(tag: String): Int {
        val dialog = node(TerminalTags.SETTINGS_DIALOG)
        val button = node(tag).fetchSemanticsNode().boundsInRoot
        val origin = dialog.fetchSemanticsNode().boundsInRoot
        val x = (button.left - origin.left).toInt() + 1
        val y = ((button.top + button.bottom) / 2 - origin.top).toInt()
        return dialog.captureToImage().toPixelMap()[x, y].toArgb()
    }

    @Test
    fun `focused control shows the focus frame`() =
        runComposeUiTest {
            // Given the open dialog with no focus on the Cancel button
            showDialog()
            edgePixel(TerminalTags.SETTINGS_CANCEL) shouldNotBe frame

            // When the Cancel button gets the focus
            node(TerminalTags.SETTINGS_CANCEL).requestFocus()
            waitUntilExactlyOneExists(hasTestTag(TerminalTags.SETTINGS_CANCEL) and isFocused(), WAIT_MS)
            waitForIdle()

            // Then its edge is drawn in the colour text
            edgePixel(TerminalTags.SETTINGS_CANCEL) shouldBe frame
        }

    @Test
    fun `focus frame keeps layout when focus changes`() =
        runComposeUiTest {
            // Given the focus on Save, and the size of Cancel without focus
            showDialog()
            node(TerminalTags.SETTINGS_SAVE).requestFocus()
            waitForIdle()
            val before = node(TerminalTags.SETTINGS_CANCEL).fetchSemanticsNode().size
            val dialogBefore = node(TerminalTags.SETTINGS_DIALOG).fetchSemanticsNode().size

            // When Tab moves the focus to Cancel
            node(TerminalTags.SETTINGS_SAVE).performKeyInput { pressKey(Key.Tab) }
            waitUntilExactlyOneExists(hasTestTag(TerminalTags.SETTINGS_CANCEL) and isFocused(), WAIT_MS)

            // Then neither the button nor the dialog changed size
            node(TerminalTags.SETTINGS_CANCEL).fetchSemanticsNode().size shouldBe before
            node(TerminalTags.SETTINGS_DIALOG).fetchSemanticsNode().size shouldBe dialogBefore
        }
}
