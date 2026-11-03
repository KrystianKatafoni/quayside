package dev.quayside.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AmButtonTest {
    private fun SemanticsNodeInteraction.focus() = performSemanticsAction(SemanticsActions.RequestFocus)

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    @Test
    fun `click calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmButton("Save", onClick = { clicks++ }) } }

            onNodeWithText("Save").performClick()

            clicks shouldBe 1
        }

    @Test
    fun `primary button click calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmButton("Apply", onClick = { clicks++ }, kind = ButtonKind.PRIMARY) } }

            onNodeWithText("Apply").performClick()

            clicks shouldBe 1
        }

    @Test
    fun `disabled button ignores click and is announced disabled`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmButton("Save", onClick = { clicks++ }, enabled = false) } }

            onNodeWithText("Save").performClick()

            clicks shouldBe 0
            onNodeWithText("Save").assertIsNotEnabled()
        }

    @Test
    fun `enabled button is announced enabled`() =
        runComposeUiTest {
            setContent { PlexTheme { AmButton("Save", onClick = {}) } }

            onNodeWithText("Save").assertIsEnabled()
        }

    @Test
    fun `button has Button role and its label`() =
        runComposeUiTest {
            setContent { PlexTheme { AmButton("Save", onClick = {}) } }

            onNodeWithText("Save")
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }

    @Test
    fun `space on focused button clicks`() =
        runComposeUiTest {
            // Given a focused button
            var clicks = 0
            setContent { PlexTheme { AmButton("Save", onClick = { clicks++ }) } }
            onNodeWithText("Save").focus()
            onNodeWithText("Save").assertIsFocused()

            // When the user presses space
            press(Key.Spacebar)

            // Then it is clicked once
            clicks shouldBe 1
        }

    @Test
    fun `enter on focused button clicks`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmButton("Save", onClick = { clicks++ }) } }
            onNodeWithText("Save").focus()

            press(Key.Enter)

            clicks shouldBe 1
        }

    @Test
    fun `tab moves focus between buttons`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        AmButton("First", onClick = {})
                        AmButton("Second", onClick = {})
                    }
                }
            }
            onNodeWithText("First").focus()
            onNodeWithText("First").assertIsFocused()

            press(Key.Tab)

            onNodeWithText("Second").assertIsFocused()
        }
}
