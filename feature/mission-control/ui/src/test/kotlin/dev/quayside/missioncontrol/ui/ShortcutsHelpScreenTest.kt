package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.designsystem.AmTestTheme
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** "? skróty klawiszowe" at the bottom of the left column (AM-816): the toggle and the cheat sheet above it. */
@OptIn(ExperimentalTestApi::class)
internal class ShortcutsHelpScreenTest {
    @Test
    fun `FR-K3 shortcuts help opens the cheat sheet`() =
        runComposeUiTest {
            // Given the toggle, closed at first
            setContent { AmTestTheme { ShortcutsHelp() } }
            awaitTag(MissionControlTags.SHORTCUTS_TOGGLE).assertTextContains("? skróty klawiszowe")
            hasTag(MissionControlTags.SHORTCUTS) shouldBe false

            // When the user clicks it, then the cheat sheet shows above the toggle and the toggle has no focus
            onNodeWithTag(MissionControlTags.SHORTCUTS_TOGGLE).performClick()
            awaitTag(MissionControlTags.SHORTCUTS)
            onNodeWithTag(MissionControlTags.SHORTCUTS_TOGGLE).assertIsNotFocused()
            val sheet = onNodeWithTag(MissionControlTags.SHORTCUTS).fetchSemanticsNode().boundsInRoot
            val toggle = onNodeWithTag(MissionControlTags.SHORTCUTS_TOGGLE).fetchSemanticsNode().boundsInRoot
            sheet.bottom shouldBeLessThanOrEqualTo toggle.top

            // And a second click hides it
            onNodeWithTag(MissionControlTags.SHORTCUTS_TOGGLE).performClick()
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.SHORTCUTS))
        }

    @Test
    fun `space after clicking the toggle reaches the window handler`() =
        runComposeUiTest {
            var spaces = 0
            setContent {
                AmTestTheme {
                    Box(
                        Modifier
                            .testTag(WINDOW)
                            .onKeyEvent {
                                (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                    if (hit) spaces++
                                }
                            }.focusable(),
                    ) { ShortcutsHelp() }
                }
            }
            onNodeWithTag(WINDOW).performSemanticsAction(SemanticsActions.RequestFocus)

            awaitTag(MissionControlTags.SHORTCUTS_TOGGLE).performClick()
            onRoot().performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()

            spaces shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    private companion object {
        const val WINDOW = "window"
    }
}
