package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Tile of a tone: background of the tone colours, icon in the tone mark colour, corner from the theme shape. */
@OptIn(ExperimentalTestApi::class)
class AmIconTileTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()

    private fun ComposeUiTest.setTile(
        tone: StatusTone,
        description: String? = null,
        square: Boolean = false,
    ) = setContent {
        Scaled {
            val shape = if (square) squareShape() else SampleTokens.shape()
            AmTheme(colors, shape, PLEX_SPEC) {
                Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                    AmIconTile(IconKind.WAITING, tone, description, Modifier.size(48.dp))
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusTone::class)
    fun `tile draws tone background and icon in tone colour`(tone: StatusTone) =
        runComposeUiTest {
            setTile(tone)
            val expected = chipColors(tone, colors)

            val pixels = onNodeWithTag(BOX).pixels()

            // beside the icon (half the tile, centred), inside the rounded corner
            pixels.argbAt(px(4), pixels.height / 2) shouldBe expected.background.argb32()
            pixels.distinctColors() shouldContain expected.mark.argb32()
            (expected.background == expected.mark) shouldBe false
        }

    @Test
    fun `tile corner follows the theme shape`() =
        runComposeUiTest {
            setTile(StatusTone.OK)
            val rounded = onNodeWithTag(BOX).pixels()

            // plex has an 8 dp corner: the very corner shows the container
            rounded.argbAt(0, 0) shouldBe container
        }

    @Test
    fun `tile of a square theme fills the corner`() =
        runComposeUiTest {
            setTile(StatusTone.OK, square = true)
            val square = onNodeWithTag(BOX).pixels()

            square.argbAt(0, 0) shouldBe toneBackground(StatusTone.OK, colors).argb32()
        }

    @Test
    fun `tile with description is one image node`() =
        runComposeUiTest {
            setTile(StatusTone.WARN, description = "Waiting for you")

            onNodeWithContentDescription("Waiting for you")
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
            onAllNodesWithContentDescription("Waiting for you", useUnmergedTree = true).assertCountEquals(1)
        }

    private companion object {
        const val BOX = "box"
    }
}
