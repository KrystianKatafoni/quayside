package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Focus, keys and the failed start of the "Nowy agent" dialog in the whole window on fakes (NFR-6, FR-S3, AM-608).
 * The dialog is a focusable popup, so keys reach its fields and not the window behind it.
 */
@OptIn(ExperimentalTestApi::class)
class NewAgentKeysWindowTest {
    private val world = AgentWorld()

    private val tabOrder =
        listOf(
            WindowTags.NEW_AGENT_PROMPT,
            WindowTags.NEW_AGENT_NAME,
            WindowTags.NEW_AGENT_AGENT,
            WindowTags.NEW_AGENT_MODEL,
            WindowTags.NEW_AGENT_PERMISSION,
            WindowTags.NEW_AGENT_CANCEL,
            WindowTags.NEW_AGENT_START,
        )

    @Test
    fun `FR-S3 enter in the prompt adds a line and does not start`() =
        runComposeUiTest {
            // Given the open dialog with the focus on the prompt
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")

            // When the user types a line, presses Enter and types another line
            typePrompt("first")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).press(Key.Enter)
            waitForIdle()
            typePrompt("second")

            // Then the prompt has two lines and nothing started
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("first\nsecond")
            world.fake.calls.shouldBeEmpty()
            onNodeWithTag(WindowTags.NEW_AGENT_DIALOG).assertExists()
        }

    @Test
    fun `NFR-6 initial focus is on the prompt, not on Uruchom`() =
        runComposeUiTest {
            // Given the dialog just opened with a project selected
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")

            // Then the focus is on the prompt and on none of the buttons
            onNodeWithTag(WindowTags.NEW_AGENT_START).assertIsNotFocused()
            onNodeWithTag(WindowTags.NEW_AGENT_CANCEL).assertIsNotFocused()

            // When the user types a prompt (so that a start would be valid) and presses Space and then Enter
            typePrompt("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).press(Key.Spacebar)
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).press(Key.Enter)
            waitForIdle()

            // Then no agent started and the dialog is still open
            world.fake.calls.shouldBeEmpty()
            world.starts.specs.shouldBeEmpty()
            onNodeWithTag(WindowTags.NEW_AGENT_DIALOG).assertExists()
        }

    @Test
    fun `NFR-6 tab moves through the fields in order`() =
        runComposeUiTest {
            // Given the open dialog with some text in the prompt
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("abc")

            // When the user presses Tab once per field
            tabOrder.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).press(Key.Tab)

                // Then the focus moves to the next one: prompt, name, subagent, model, mode, Anuluj, Uruchom
                awaitFocus(to)
            }

            // And the prompt got no tab character
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("abc")

            // When the user presses Shift Tab on the prompt, the focus goes to the project
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).performClick()
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            awaitFocus(WindowTags.NEW_AGENT_PROJECT)
        }

    @Test
    fun `FR-S3 escape closes without a start`() =
        runComposeUiTest {
            // Given the open dialog with a prompt
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")

            // When the user presses Escape
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).press(Key.Escape)

            // Then the dialog is gone and nothing started
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.fake.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-S3 a reopened dialog is empty after a cancel and after a start`() =
        runComposeUiTest {
            // Given a cancelled dialog that had a prompt
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("old prompt")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).press(Key.Escape)
            awaitGone(WindowTags.NEW_AGENT_DIALOG)

            // When the user opens it again
            pressCmdN(world.setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)

            // Then the prompt is empty; after a start and one more opening it is empty again
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("")
            typePrompt("new prompt")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            pressCmdN(world.setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("")
            onNodeWithTag(WindowTags.NEW_AGENT_PROBLEM).assertDoesNotExist()
        }

    @Test
    fun `FR-S3 failed start shows the failure text and keeps the prompt`() =
        runComposeUiTest {
            // Given the open dialog with a prompt and a start that Claude Code refuses
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")
            world.fake.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            // When the user clicks "Uruchom"
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()

            // Then the dialog stays with the failure text (no longer a trust hint, AM-309), and the prompt is kept
            awaitText(WindowTags.NEW_AGENT_PROBLEM, "odmówił startu")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_DIALOG).assertExists()
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            world.fake.calls shouldBe listOf("start project0001")

            // And the same form starts when the user tries again
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.fake.calls shouldBe listOf("start project0001", "start project0001")
            awaitDetails("00000001")
        }

    @Test
    fun `FR-S3 window shortcuts do nothing while the dialog is open`() =
        runComposeUiTest {
            // Given the open dialog
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")

            // When the user presses g and p (the project switch chord)
            listOf(Key.G, Key.P).forEach { key ->
                onNodeWithTag(WindowTags.KEYS).performKeyInput {
                    keyDown(key)
                    keyUp(key)
                }
                waitForIdle()
            }

            // Then no palette opened and the dialog is still open
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            onNodeWithTag(WindowTags.NEW_AGENT_DIALOG).assertExists()
        }
}
