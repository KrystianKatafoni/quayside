package dev.quayside.core.designsystem

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AmListRowTest {
    @Test
    fun `selected row is announced selected and shows check`() =
        runComposeUiTest {
            setContent { PlexTheme { AmListRow(selected = true, onClick = {}) { AmText("Plex Violet") } } }

            onNodeWithText("Plex Violet").assertIsSelected()
            onNodeWithText("✓", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `unselected row has no check`() =
        runComposeUiTest {
            setContent { PlexTheme { AmListRow(selected = false, onClick = {}) { AmText("Plex Violet") } } }

            onNodeWithText("Plex Violet").assertIsNotSelected()
            onNodeWithText("✓", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `click calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmListRow(selected = false, onClick = { clicks++ }) { AmText("Paper Indigo") } } }

            onNodeWithText("Paper Indigo").performClick()

            clicks shouldBe 1
        }

    @Test
    fun `enter on focused row calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmListRow(selected = false, onClick = { clicks++ }) { AmText("Paper Indigo") } } }
            onNodeWithText("Paper Indigo").performSemanticsAction(SemanticsActions.RequestFocus)

            onRoot().performKeyInput {
                keyDown(Key.Enter)
                keyUp(Key.Enter)
            }
            waitForIdle()

            clicks shouldBe 1
        }
}
