package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import androidx.compose.ui.unit.width as boundsWidth

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
            setContent {
                PlexTheme {
                    AmButton(
                        "Apply",
                        onClick = { clicks++ },
                        options = ButtonOptions(kind = ButtonKind.PRIMARY),
                    )
                }
            }

            onNodeWithText("Apply").performClick()

            clicks shouldBe 1
        }

    @Test
    fun `disabled button ignores click and is announced disabled`() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                PlexTheme {
                    AmButton(
                        "Save",
                        onClick = { clicks++ },
                        options = ButtonOptions(enabled = false),
                    )
                }
            }

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

    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()
    private val plain = ButtonOptions(focusable = false)

    /** Two copies in a [Scaled] column: the subject ([BOX]) and, to compare, one with [other] ([NO_ICON]). */
    private fun ComposeUiTest.setPixels(
        options: ButtonOptions,
        other: ButtonOptions = plain.copy(kind = options.kind),
    ) = setContent {
        Scaled {
            PlexTheme {
                Column {
                    Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                        AmButton("MMMM", onClick = {}, options = options)
                    }
                    Box(Modifier.testTag(NO_ICON).background(colors.surfaces.bg.toColor())) {
                        AmButton("MMMM", onClick = {}, options = other)
                    }
                }
            }
        }
    }

    /** Length in pixels of the first run of [argb] on the middle row; 0 when the colour is not on that row. */
    private fun PixelMap.firstRunOnMiddleRow(argb: Int): Int {
        val row = height / 2
        val start = (0 until width).firstOrNull { argbAt(it, row) == argb } ?: return 0
        return (start until width).takeWhile { argbAt(it, row) == argb }.size
    }

    /** Last column that has a pixel of [argb] anywhere. */
    private fun PixelMap.lastColumnOf(argb: Int): Int = positionsOf(argb).maxOf { it.first }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ButtonKind::class, names = ["PRIMARY", "SECONDARY"])
    fun `icon is drawn before the label in label colour`(kind: ButtonKind) =
        runComposeUiTest {
            val label = buttonColors(kind, enabled = true, tokens = colors).label.argb32()
            setPixels(plain.copy(kind = kind, icon = IconKind.PLUS))
            val withIcon = onNodeWithTag(BOX).pixels()
            val without = onNodeWithTag(NO_ICON).pixels()

            // The plus bar is a long run on the middle row and it comes first; a glyph stroke is short
            withIcon.firstRunOnMiddleRow(label) shouldBeGreaterThan px(6)
            without.firstRunOnMiddleRow(label) shouldBeLessThan px(6)
            withIcon.width shouldBeGreaterThan without.width + px(15)
        }

    @Test
    fun `shortcut is drawn after the label`() =
        runComposeUiTest {
            val label = buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = colors).label.argb32()
            setPixels(plain.copy(shortcut = "MMM"))
            val withShortcut = onNodeWithTag(BOX).pixels()
            val without = onNodeWithTag(NO_ICON).pixels()

            withShortcut.width shouldBeGreaterThan without.width
            withShortcut.lastColumnOf(label) shouldBeGreaterThan without.lastColumnOf(label) + px(8)
        }

    @Test
    fun `label and shortcut are read as one button`() =
        runComposeUiTest {
            setContent { PlexTheme { AmButton("Save", onClick = {}, options = ButtonOptions(shortcut = "\u2318N")) } }

            onAllNodes(hasClickAction()).assertCountEquals(1)
            onAllNodes(hasClickAction())[0].assertTextEquals("Save", "\u2318N")
        }

    @Test
    fun `danger draws err outline without fill`() =
        runComposeUiTest {
            setPixels(plain.copy(kind = ButtonKind.DANGER))
            val pixels = onNodeWithTag(BOX).pixels()
            val outline =
                colors.surfaces.panel
                    .over(colors.surfaces.bg)
                    .mix(colors.status.err, 35)

            pixels.argbAt(pixels.width / 2, 0) shouldBe outline.argb32()
            pixels.argbAt(px(4), pixels.height / 2) shouldBe container
            pixels.distinctColors() shouldContain colors.status.err.argb32()
        }

    @Test
    fun `primary draws accent fill`() =
        runComposeUiTest {
            setPixels(plain.copy(kind = ButtonKind.PRIMARY))
            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(px(4), pixels.height / 2) shouldBe colors.content.accent.argb32()
        }

    @Test
    fun `non focusable button is skipped by tab`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        AmButton("First", onClick = {}, options = plain)
                        AmButton("Second", onClick = {})
                    }
                }
            }

            press(Key.Tab)

            onNodeWithText("Second").assertIsFocused()
            onNodeWithText("First").assertIsNotFocused()
        }

    @Test
    fun `click does not focus a non focusable button`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmButton("Save", onClick = { clicks++ }, options = plain) } }

            onNodeWithText("Save").performClick()

            clicks shouldBe 1
            onNodeWithText("Save").assertIsNotFocused()
        }

    @Test
    fun `space after clicking a non focusable button reaches the window handler`() =
        runComposeUiTest {
            // Given a window handler with focus and a non focusable button inside it
            var spaces = 0
            var clicks = 0
            setContent {
                PlexTheme {
                    Box(
                        Modifier
                            .testTag(WINDOW)
                            .onKeyEvent {
                                (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                    if (hit) spaces++
                                }
                            }.focusable(),
                    ) {
                        AmButton("Save", onClick = { clicks++ }, options = plain)
                    }
                }
            }
            onNodeWithTag(WINDOW).focus()

            // When the user clicks the button and presses space
            onNodeWithText("Save").performClick()
            press(Key.Spacebar)

            // Then the window handler gets the space and the button is not clicked again
            spaces shouldBe 1
            clicks shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    @Test
    fun `non focusable button reserves no frame space`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        Box(Modifier.testTag(FOCUSABLE)) { AmButton("Save", onClick = {}) }
                        Box(Modifier.testTag(PLAIN)) { AmButton("Save", onClick = {}, options = plain) }
                    }
                }
            }

            val withFrame = onNodeWithTag(FOCUSABLE).getUnclippedBoundsInRoot()
            val without = onNodeWithTag(PLAIN).getUnclippedBoundsInRoot()

            (withFrame.boundsWidth - without.boundsWidth).value shouldBe 8f
            (withFrame.height - without.height).value shouldBe 8f
        }

    @Test
    fun `wide button centres its content`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Box(Modifier.width(300.dp)) {
                        AmButton("Save", onClick = {}, Modifier.fillMaxWidth().testTag(BOX), options = plain)
                    }
                }
            }

            val button = onNodeWithTag(BOX).getUnclippedBoundsInRoot()
            val text = onNodeWithText("Save", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val left = text.left - button.left
            val right = button.right - text.right

            button.boundsWidth.value shouldBe 300f
            kotlin.math.abs((left - right).value) shouldBeLessThan 1f
        }

    private companion object {
        const val BOX = "box"
        const val NO_ICON = "noIcon"
        const val WINDOW = "window"
        const val FOCUSABLE = "focusable"
        const val PLAIN = "plain"
    }
}
