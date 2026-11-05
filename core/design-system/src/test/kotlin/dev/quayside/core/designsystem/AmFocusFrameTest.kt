package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The focus frame is checked on pixels of the wrapper (container) of the button, because the semantics node of the
 * button starts inside the frame and its own image would not show it: 2 dp frame in the text colour at the edge,
 * then a 2 dp gap that shows the container background (so the frame never replaces the edge of an accent button),
 * then the button.
 * The first button of a column is the subject; focus moves to the second one and back.
 */
@OptIn(ExperimentalTestApi::class)
class AmFocusFrameTest {
    private val colors = SampleTokens.colors()
    private val frame = colors.content.text.argb32()
    private val container = colors.surfaces.bg.argb32()

    private fun SemanticsNodeInteraction.focus() = performSemanticsAction(SemanticsActions.RequestFocus)

    private fun ComposeUiTest.setButtons(kind: ButtonKind) =
        setContent {
            PlexTheme {
                Column {
                    Box(Modifier.testTag(FIRST).background(colors.surfaces.bg.toColor())) {
                        AmButton("First", onClick = {}, options = ButtonOptions(kind = kind))
                    }
                    AmButton("Second", onClick = {}, options = ButtonOptions(kind = kind))
                }
            }
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ButtonKind::class)
    fun `focused button has a 2 dp frame in text colour at its edge`(kind: ButtonKind) =
        runComposeUiTest {
            setButtons(kind)
            val unfocused = onNodeWithTag(FIRST).pixels()
            val edge = (2 * density.density).toInt()
            val mid = unfocused.height / 2

            onNodeWithText("First").focus()
            val focused = onNodeWithTag(FIRST).pixels()

            (0 until edge).forEach { offset ->
                focused.argbAt(offset, mid) shouldBe frame
                focused.argbAt(focused.width / 2, offset) shouldBe frame
                unfocused.argbAt(offset, mid) shouldBe container
                unfocused.argbAt(unfocused.width / 2, offset) shouldBe container
            }
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ButtonKind::class)
    fun `frame is separated from the button by the container background and is not accent`(kind: ButtonKind) =
        runComposeUiTest {
            setButtons(kind)
            onNodeWithText("First").focus()
            val focused = onNodeWithTag(FIRST).pixels()
            val mid = focused.height / 2
            val edge = (2 * density.density).toInt()
            val gap = (2 * density.density).toInt()

            (edge until edge + gap).forEach { offset ->
                focused.argbAt(offset, mid) shouldBe container
                focused.argbAt(focused.width / 2, offset) shouldBe container
            }
            (frame == colors.content.accent.argb32()) shouldBe false
            (container == colors.content.accent.argb32()) shouldBe false
        }

    @Test
    fun `primary button keeps its accent fill right after the gap`() =
        runComposeUiTest {
            setButtons(ButtonKind.PRIMARY)
            val inner = (4 * density.density).toInt()
            val pixels = onNodeWithTag(FIRST).pixels()

            pixels.argbAt(inner, pixels.height / 2) shouldBe colors.content.accent.argb32()
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ButtonKind::class)
    fun `frame disappears when focus leaves and the layout does not move`(kind: ButtonKind) =
        runComposeUiTest {
            setButtons(kind)
            val before = onNodeWithTag(FIRST).pixels()
            onNodeWithText("First").focus()
            val focusedPixels = onNodeWithTag(FIRST).pixels()
            val mid = before.height / 2

            onNodeWithText("Second").focus()
            val after = onNodeWithTag(FIRST).pixels()

            focusedPixels.argbAt(0, mid) shouldBe frame
            after.argbAt(0, mid) shouldBe container
            after.argbAt(after.width / 2, 0) shouldBe container
            listOf(focusedPixels, after).forEach {
                it.width shouldBe before.width
                it.height shouldBe before.height
            }
        }

    private companion object {
        const val FIRST = "first"
    }
}
