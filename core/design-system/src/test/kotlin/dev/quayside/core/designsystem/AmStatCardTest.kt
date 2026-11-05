package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import org.junit.jupiter.api.Test

/** Counter tile: one announced node, colours of text, label, tile and panel on pixels (4 px per dp). */
@OptIn(ExperimentalTestApi::class)
class AmStatCardTest {
    private val colors = SampleTokens.colors()

    /** What a screen reader says for the node: its description, otherwise its text. */
    private fun SemanticsNode.announced(): String? =
        config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .ifEmpty { config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }
            .joinToString(" ")
            .ifEmpty { null }

    @Test
    fun `stat card is read as one node with value and label`() =
        runComposeUiTest {
            setContent { PlexTheme { AmStatCard("12", "Waiting", IconKind.WAITING, tone = StatusTone.WARN) } }

            onAllNodes(SemanticsMatcher("announced as '12 Waiting'") { it.announced() == "12 Waiting" })
                .assertCountEquals(1)
            // the tile icon is decorative: the parent carries the description, no extra image node
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image), useUnmergedTree = true)
                .assertCountEquals(0)
        }

    @Test
    fun `value is drawn in text and tile in tone colours`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                            AmStatCard("MM", "WWW", IconKind.WAITING, tone = StatusTone.WARN)
                        }
                    }
                }
            }
            val tile = chipColors(StatusTone.WARN, colors)

            val shown = onNodeWithTag(BOX).pixels().distinctColors()

            shown shouldContain colors.content.text.argb32()
            shown shouldContain colors.content.muted.argb32()
            shown shouldContain tile.background.argb32()
            shown shouldContain tile.mark.argb32()
            shown shouldContain colors.surfaces.panel.argb32()
            shown shouldContain colors.surfaces.border.argb32()
        }

    @Test
    fun `stat card is not focusable`() =
        runComposeUiTest {
            setContent { PlexTheme { Box(Modifier.testTag(BOX)) { AmStatCard("12", "Waiting", IconKind.WAITING) } } }

            press(Key.Tab)

            onNodeWithTag(BOX).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
                .assertCountEquals(0)
        }

    private companion object {
        const val BOX = "box"
    }
}
