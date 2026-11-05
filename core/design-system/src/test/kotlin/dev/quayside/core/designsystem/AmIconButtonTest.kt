package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Icon button: semantics and keys on plain content; colours and the focus frame on pixels of a wrapper with the
 * container background, at 4 px per dp. Pixel tests use the non focusable variant (no reserved frame) unless they
 * check the frame; the icon is PLUS, whose crossing is the centre of the target.
 */
@OptIn(ExperimentalTestApi::class)
class AmIconButtonTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()
    private val plain = IconButtonOptions(focusable = false)

    private fun ComposeUiTest.setPlain(
        options: IconButtonOptions = IconButtonOptions(),
        onClick: () -> Unit = {},
    ) = setContent { PlexTheme { AmIconButton(IconKind.PLUS, LABEL, onClick, options = options) } }

    private fun ComposeUiTest.setPixels(
        options: IconButtonOptions,
        label: String = LABEL,
    ) = setContent {
        Scaled {
            PlexTheme {
                Column {
                    Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                        AmIconButton(IconKind.PLUS, label, onClick = {}, options = options)
                    }
                    AmIconButton(IconKind.PLUS, OTHER, onClick = {})
                }
            }
        }
    }

    @Test
    fun `label is the content description and role is Button`() =
        runComposeUiTest {
            setPlain()

            onNodeWithContentDescription(LABEL)
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }

    @Test
    fun `label is announced once`() =
        runComposeUiTest {
            setPlain()

            onAllNodesWithContentDescription(LABEL, useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `click calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setPlain(onClick = { clicks++ })

            onNodeWithContentDescription(LABEL).performClick()

            clicks shouldBe 1
        }

    @Test
    fun `space on focused icon button clicks`() =
        runComposeUiTest {
            var clicks = 0
            setPlain(onClick = { clicks++ })
            onNodeWithContentDescription(LABEL).focus()
            onNodeWithContentDescription(LABEL).assertIsFocused()

            press(Key.Spacebar)

            clicks shouldBe 1
        }

    @Test
    fun `enter on focused icon button clicks`() =
        runComposeUiTest {
            var clicks = 0
            setPlain(onClick = { clicks++ })
            onNodeWithContentDescription(LABEL).focus()

            press(Key.Enter)

            clicks shouldBe 1
        }

    @Test
    fun `tab reaches focusable icon button`() =
        runComposeUiTest {
            setPlain()

            press(Key.Tab)

            onNodeWithContentDescription(LABEL).assertIsFocused()
        }

    @Test
    fun `focused icon button shows the focus frame`() =
        runComposeUiTest {
            setPixels(IconButtonOptions())
            val unfocused = onNodeWithTag(BOX).pixels()
            val mid = unfocused.height / 2

            onNodeWithContentDescription(LABEL).focus()
            val focused = onNodeWithTag(BOX).pixels()

            // 2 dp frame in the text colour at the edge of the wrapper, nothing there before focus
            (0 until px(2)).forEach {
                focused.argbAt(it, mid) shouldBe colors.content.text.argb32()
                unfocused.argbAt(it, mid) shouldBe container
            }
            focused.width shouldBe unfocused.width
        }

    @Test
    fun `non focusable icon button is skipped by tab`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        AmIconButton(IconKind.PLUS, LABEL, onClick = {}, options = plain)
                        AmIconButton(IconKind.PLUS, OTHER, onClick = {})
                    }
                }
            }

            press(Key.Tab)

            onNodeWithContentDescription(OTHER).assertIsFocused()
            onNodeWithContentDescription(LABEL).assert(NOT_FOCUSED)
        }

    @Test
    fun `click does not focus a non focusable icon button`() =
        runComposeUiTest {
            var clicks = 0
            setPlain(plain, onClick = { clicks++ })

            onNodeWithContentDescription(LABEL).performClick()

            clicks shouldBe 1
            onNodeWithContentDescription(LABEL).assert(NOT_FOCUSED)
        }

    @Test
    fun `space after clicking a non focusable icon button reaches the window handler`() =
        runComposeUiTest {
            // Given a window handler with focus and a non focusable icon button inside it
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
                        AmIconButton(IconKind.PLUS, LABEL, onClick = { clicks++ }, options = plain)
                    }
                }
            }
            onNodeWithTag(WINDOW).focus()

            // When the user clicks the button and presses space
            onNodeWithContentDescription(LABEL).performClick()
            press(Key.Spacebar)

            // Then the window handler gets the space and the button is not clicked again
            spaces shouldBe 1
            clicks shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    @Test
    fun `non focusable icon button reserves no frame space`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        Box(Modifier.testTag(FOCUSABLE)) { AmIconButton(IconKind.PLUS, LABEL, onClick = {}) }
                        Box(
                            Modifier.testTag(PLAIN),
                        ) { AmIconButton(IconKind.PLUS, OTHER, onClick = {}, options = plain) }
                    }
                }
            }

            val withFrame = onNodeWithTag(FOCUSABLE).getUnclippedBoundsInRoot()
            val without = onNodeWithTag(PLAIN).getUnclippedBoundsInRoot()

            (withFrame.width - without.width).value shouldBe 8f
            (withFrame.height - without.height).value shouldBe 8f
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconButtonSize::class)
    fun `size sets the target`(size: IconButtonSize) =
        runComposeUiTest {
            val expected =
                when (size) {
                    IconButtonSize.COMPACT -> 28.dp
                    IconButtonSize.REGULAR -> 34.dp
                    IconButtonSize.LARGE -> 44.dp
                }
            setPlain(IconButtonOptions(size = size))

            onNodeWithContentDescription(LABEL).assertWidthIsEqualTo(expected).assertHeightIsEqualTo(expected)
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconButtonStyle::class)
    fun `style draws its background border and icon`(style: IconButtonStyle) =
        runComposeUiTest {
            val options = plain.copy(style = style)
            setPixels(options)
            val expected = iconButtonColors(options, colors)
            val pixels = onNodeWithTag(BOX).pixels()
            val inside = if (expected.background.alpha == 0) container else expected.background.argb32()
            val edge = expected.border?.argb32() ?: inside

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe expected.icon.argb32()
            pixels.argbAt(px(4), pixels.height / 2) shouldBe inside
            pixels.argbAt(pixels.width / 2, 0) shouldBe edge
            (expected.icon.argb32() == inside) shouldBe false
        }

    @Test
    fun `danger ignores tone and draws err`() =
        runComposeUiTest {
            setPixels(plain.copy(style = IconButtonStyle.DANGER, tone = StatusTone.OK))

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe colors.status.err.argb32()
            pixels.distinctColors() shouldNotContain colors.status.ok.argb32()
        }

    @Test
    fun `tone colours the icon of a ghost button`() =
        runComposeUiTest {
            setPixels(plain.copy(tone = StatusTone.WARN))

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe colors.status.warn.argb32()
        }

    @Test
    fun `badge draws counter on warn and adds no semantics node`() =
        runComposeUiTest {
            setPixels(plain.copy(badge = "2"), label = "Notifications, 2 new")
            val (warn, ink) = counterBadgeColors(colors)

            val pixels = onNodeWithTag(BOX).pixels()
            val circle = pixels.positionsOf(warn.argb32())

            // the counter sits on the top-right corner and carries the ink digit
            (circle.isNotEmpty()) shouldBe true
            circle.all { it.first > pixels.width / 2 && it.second < pixels.height / 2 } shouldBe true
            pixels.distinctColors() shouldContain ink.argb32()
            onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithContentDescription("Notifications, 2 new")
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        }

    @Test
    fun `button without badge draws no counter`() =
        runComposeUiTest {
            setPixels(plain)

            onNodeWithTag(BOX).pixels().distinctColors() shouldNotContain colors.status.warn.argb32()
        }

    @Test
    fun `disabled icon button ignores click, is announced disabled and draws dim`() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                            AmIconButton(IconKind.PLUS, LABEL, { clicks++ }, options = plain.copy(enabled = false))
                        }
                    }
                }
            }

            onNodeWithContentDescription(LABEL).performClick()

            clicks shouldBe 0
            onNodeWithContentDescription(LABEL).assertIsNotEnabled()
            val pixels = onNodeWithTag(BOX).pixels()
            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe colors.content.dim.argb32()
        }

    @Test
    fun `disabled icon button is not focused by tab`() =
        runComposeUiTest {
            setPlain(IconButtonOptions(enabled = false))

            press(Key.Tab)

            onNodeWithContentDescription(LABEL).assert(NOT_FOCUSED)
        }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(BlankLabel::class)
    fun `blank label throws IllegalArgumentException`(blank: BlankLabel) {
        shouldThrow<IllegalArgumentException> {
            runComposeUiTest {
                setContent { PlexTheme { AmIconButton(IconKind.PLUS, blank.text, onClick = {}) } }
            }
        }
    }

    /** Labels without a visible character. */
    enum class BlankLabel(
        val text: String,
    ) {
        EMPTY(""),
        SPACES("   "),
        TAB_AND_NEWLINE("\t\n"),
    }

    private companion object {
        /** Not focused: the node has no Focused flag (no focus target) or it is false. */
        val NOT_FOCUSED =
            SemanticsMatcher("is not focused") { it.config.getOrElse(SemanticsProperties.Focused) { false } != true }
        const val LABEL = "Add"
        const val OTHER = "Other"
        const val BOX = "box"
        const val WINDOW = "window"
        const val FOCUSABLE = "focusable"
        const val PLAIN = "plain"
    }
}
