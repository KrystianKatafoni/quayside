package dev.quayside.appearance.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.ListDensity
import org.junit.jupiter.api.Test

/**
 * The preview is skipped by Tab in both directions: the focus goes from the density straight to the terminal fonts and
 * back, and never gets stuck in front of the preview (plan AM-803, risk "Niefokusowalność podglądu").
 */
@OptIn(ExperimentalTestApi::class)
class PreviewTabOrderTest {
    private fun androidx.compose.ui.test.ComposeUiTest.press(shift: Boolean) {
        onNodeWithTag(AppearanceTags.DIALOG).performKeyInput {
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            if (shift) keyUp(Key.ShiftLeft)
        }
        waitForIdle()
    }

    @Test
    fun `tab goes from the density past the preview to the first terminal font`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            await(AppearanceTags.density(ListDensity.COMFORTABLE)).requestFocus()
            awaitFocus(AppearanceTags.density(ListDensity.COMFORTABLE))

            press(shift = false)

            awaitFocus(AppearanceTags.terminalFont(null))
            onNodeWithTag(SampleTags.FOCUSABLE).assertIsNotFocused()
        }

    @Test
    fun `shift tab goes from the first terminal font past the preview to the density`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            await(AppearanceTags.terminalFont(null)).requestFocus()
            awaitFocus(AppearanceTags.terminalFont(null))

            press(shift = true)

            awaitFocus(AppearanceTags.density(ListDensity.COMFORTABLE))
            onNodeWithTag(SampleTags.FOCUSABLE).assertIsNotFocused()
        }
}
