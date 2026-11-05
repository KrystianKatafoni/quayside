package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Shortcut mark: muted mono text and a border2 frame; plain text for screen readers. */
@OptIn(ExperimentalTestApi::class)
class AmKeyCapTest {
    private val colors = SampleTokens.colors()

    @Test
    fun `key cap shows keys in mono and draws a border2 frame`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag(BOX).background(colors.surfaces.panel.toColor())) { AmKeyCap("MMMM") }
                    }
                }
            }

            val pixels = onNodeWithTag(BOX).pixels()
            val shown = pixels.distinctColors()
            val (text, border) = keyCapColors(colors)

            // frame: top edge in the middle of the cap
            pixels.argbAt(pixels.width / 2, 0) shouldBe border.argb32()
            shown shouldContain text.argb32()
            (text == border) shouldBe false
        }

    @Test
    fun `key cap uses the mono family of the theme`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        Box(Modifier.testTag(NARROW)) { AmKeyCap("iiiiiiiiii") }
                        Box(Modifier.testTag(WIDE)) { AmKeyCap("MMMMMMMMMM") }
                    }
                }
            }

            // in a monospace family ten narrow letters take as much room as ten wide ones
            val narrow = onNodeWithTag(NARROW).fetchSemanticsNode().size.width
            val wide = onNodeWithTag(WIDE).fetchSemanticsNode().size.width

            narrow shouldBe wide
        }

    @Test
    fun `key cap is read as plain text`() =
        runComposeUiTest {
            setContent { PlexTheme { AmKeyCap("Esc") } }

            onNodeWithText("Esc").assertExists()
        }

    @Test
    fun `key cap is not focusable`() =
        runComposeUiTest {
            setContent { PlexTheme { AmKeyCap("Esc") } }

            press(Key.Tab)

            onNodeWithText("Esc").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        }

    private companion object {
        const val BOX = "box"
        const val NARROW = "narrow"
        const val WIDE = "wide"
    }
}
