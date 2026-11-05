package dev.quayside.core.designsystem

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * The keys of `AmDropdown` (AM-817, NFR-6): which keys open the closed field, how the focus moves in the open list,
 * what selects and what closes. Decision of 2026-11-05: every arrow, side ones too, only opens the list of a closed
 * field (as in macOS), so a dangerous last value is never one key away from the first.
 */
@OptIn(ExperimentalTestApi::class)
class AmDropdownKeysTest {
    private val list = AmDropdownTags.LIST

    private fun androidx.compose.ui.test.ComposeUiTest.awaitOpen() {
        waitUntilExactlyOneExists(hasTestTag(list), DROPDOWN_WAIT_MS)
    }

    /** Shows [rig] with the field focused and its list opened by Enter. */
    private fun androidx.compose.ui.test.ComposeUiTest.opened(rig: DropdownRig) {
        show(rig)
        focusField()
        pressKey(Key.Enter)
        awaitOpen()
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Enter", "Space", "Down", "Up", "Left", "Right"])
    fun `Enter, Space and the arrow keys open the list`(name: String) =
        runComposeUiTest {
            show(DropdownRig(selected = 1))
            focusField()

            pressKey(keyNamed(name))

            awaitOpen()
        }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource("Meta,Enter", "Ctrl,Enter", "Alt,Enter", "Meta,Down", "Meta,Space")
    fun `cmd Enter is not consumed by the field`(
        modifier: String,
        key: String,
    ) = runComposeUiTest {
        val rig = DropdownRig()
        show(rig)
        focusField()

        pressWith(keyNamed(modifier), keyNamed(key))

        rig.passed shouldContain keyNamed(key)
        onNodeWithTag(list).assertDoesNotExist()
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Left", "Right", "Up", "Down", "Enter", "Space"])
    fun `no single key on the closed field selects the last option, an arrow key only opens the list`(name: String) =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            show(rig)
            focusField()

            pressKey(keyNamed(name))

            rig.picks.shouldBeEmpty()
            (rig.selected == MODES.lastIndex) shouldBe false
        }

    @Test
    fun `the focus starts on the selected option`() =
        runComposeUiTest {
            opened(DropdownRig(selected = 2))

            awaitFocus(AmDropdownTags.option(2))
        }

    @Test
    fun `with no selection the focus starts on the first option`() =
        runComposeUiTest {
            opened(DropdownRig(selected = -1, placeholder = "Pick one"))

            awaitFocus(AmDropdownTags.option(0))
        }

    @Test
    fun `Down and Up move the focus and stop at the ends`() =
        runComposeUiTest {
            opened(DropdownRig(selected = 0))

            pressKey(Key.DirectionUp)
            awaitFocus(AmDropdownTags.option(0))
            repeat(times = 3) { pressKey(Key.DirectionDown) }
            awaitFocus(AmDropdownTags.option(3))
            pressKey(Key.DirectionDown)
            awaitFocus(AmDropdownTags.option(3))
            pressKey(Key.DirectionUp)
            awaitFocus(AmDropdownTags.option(2))
        }

    @Test
    fun `moving the focus selects nothing`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            opened(rig)

            repeat(times = 3) { pressKey(Key.DirectionDown) }

            rig.picks.shouldBeEmpty()
            onNodeWithTag(list).assertExists()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Enter", "Space"])
    fun `Enter and Space select the focused option and the focus goes back to the field`(name: String) =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            opened(rig)

            repeat(times = 2) { pressKey(Key.DirectionDown) }
            pressKey(keyNamed(name))
            awaitClosed()

            rig.picks shouldBe listOf(2)
            awaitFocus(FIELD)
        }

    @Test
    fun `bypass needs the list, three steps down and Enter`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            show(rig)
            focusField()

            pressKey(Key.Enter)
            awaitOpen()
            pressKey(Key.DirectionUp)
            pressKey(Key.Enter)
            awaitClosed()
            rig.picks.shouldBeEmpty()

            pressKey(Key.Enter)
            awaitOpen()
            repeat(times = 3) { pressKey(Key.DirectionDown) }
            rig.picks.shouldBeEmpty()
            pressKey(Key.Enter)
            awaitClosed()
            rig.picks shouldBe listOf(MODES.lastIndex)
        }

    @Test
    fun `Escape closes the list only and the focus goes back to the field`() =
        runComposeUiTest {
            // Given a dropdown in a dialog popup that handles Escape itself, and an open list
            val rig = DropdownRig(selected = 1, inDialog = true)
            show(rig)
            focusField()
            pressKey(Key.Enter)
            awaitOpen()

            // When the user presses Escape
            pressKey(Key.Escape)
            awaitClosed()

            // Then the list is gone, the dialog heard nothing, nothing changed and the field has the focus
            rig.dialogEscapes shouldBe 0
            rig.dialogDismissals shouldBe 0
            rig.picks.shouldBeEmpty()
            awaitFocus(FIELD)

            // And the next Escape belongs to the dialog
            pressKey(Key.Escape)
            rig.dialogEscapes shouldBe 1
        }

    @Test
    fun `Tab closes the list with no change`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 1)
            opened(rig)

            pressKey(Key.Tab)
            awaitClosed()

            rig.picks.shouldBeEmpty()
            rig.selected shouldBe 1
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Home", "End", "PageUp", "PageDown"])
    fun `Home, End, Page Up and Page Down do not jump`(name: String) =
        runComposeUiTest {
            // Given the focus on the second of four options: a jump would land on the first or on the last
            opened(DropdownRig(selected = 1))
            awaitFocus(AmDropdownTags.option(1))

            pressKey(keyNamed(name))

            awaitFocus(AmDropdownTags.option(1))
            onNodeWithTag(list).assertExists()
        }

    @Test
    fun `the focus goes back to the field after a click on an option`() =
        runComposeUiTest {
            show(DropdownRig(selected = 0))
            openByClick()

            onNodeWithTag(AmDropdownTags.option(2)).performClick()
            awaitClosed()

            awaitFocus(FIELD)
        }

    @Test
    fun `the focus goes back to the field after Escape on a list opened by a click`() =
        runComposeUiTest {
            show(DropdownRig(selected = 0))
            openByClick()

            pressKey(Key.Escape)
            awaitClosed()

            awaitFocus(FIELD)
        }

    @Test
    fun `the focus goes back to the field after a click outside a list opened by a click`() =
        runComposeUiTest {
            show(DropdownRig(selected = 0))
            openByClick()

            onNodeWithTag(OUTSIDE).performClick()
            awaitClosed()

            awaitFocus(FIELD)
        }

    @Test
    fun `cmd Down in the open list does not move the focus`() =
        runComposeUiTest {
            opened(DropdownRig(selected = 1))
            awaitFocus(AmDropdownTags.option(1))

            onNodeWithTag(list).performKeyInput {
                keyDown(Key.MetaLeft)
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
                keyUp(Key.MetaLeft)
            }
            waitForIdle()

            awaitFocus(AmDropdownTags.option(1))
        }
}
