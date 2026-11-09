package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The mark on pixels of a wrapper with a fixed background (the mark node alone has none), at 4 pixels per dp so that
 * the shapes of 20 and 32 dp have fully covered pixels; colours are compared with a tolerance of 2 per channel.
 */
@OptIn(ExperimentalTestApi::class)
class AmLogoTest {
    private val container = Color(CONTAINER)

    private fun ComposeUiTest.setLogo(
        size: Dp,
        description: String? = null,
    ) = setContent {
        Scaled {
            PlexTheme {
                Box(Modifier.testTag(BOX).background(container)) { AmLogo(size, description) }
            }
        }
    }

    private fun PixelMap.shouldShow(
        points: List<LogoPoint>,
        sizeDp: Int,
        viewBox: Int,
    ) = points.forEach { point ->
        val x = unitToPixel(point.x, px(sizeDp), viewBox)
        val y = unitToPixel(point.y, px(sizeDp), viewBox)
        shouldBeNear(argbAt(x, y), point.expected.argb32(), clue = "${point.label} ($x, $y)")
    }

    @Test
    fun `small logo draws tile, beam, lantern and base`() =
        runComposeUiTest {
            setLogo(20.dp)

            onNodeWithTag(BOX).pixels().shouldShow(smallPoints(), sizeDp = 20, viewBox = 16)
        }

    @Test
    fun `logo at 32 dp uses the full drawing`() =
        runComposeUiTest {
            setLogo(32.dp)

            onNodeWithTag(BOX).pixels().shouldShow(fullPoints(), sizeDp = 32, viewBox = 32)
        }

    @Test
    fun `logo at 31 dp still uses the small drawing`() =
        runComposeUiTest {
            setLogo(31.dp)

            // the small beam reaches x = 13.5 of 16; the full drawing has the 90% beam on the tile there,
            // not the full #FBBF24 (red channel differs by about 22)
            val pixels = onNodeWithTag(BOX).pixels()
            val point = smallPoints().first { it.label == "main beam" }
            val x = unitToPixel(point.x, px(31), 16)
            val y = unitToPixel(point.y, px(31), 16)
            shouldBeNear(pixels.argbAt(x, y), point.expected.argb32(), clue = "beam ($x, $y)")
        }

    @Test
    fun `logo corner is rounded and transparent`() =
        runComposeUiTest {
            setLogo(32.dp)

            val pixels = onNodeWithTag(BOX).pixels()

            // full drawing: tile from 1 with rx 8, arc centre (9; 9); (1.5; 1.5) is outside, (4; 4) inside
            pixels.argbAt(unitToPixel(1.5, px(32), 32), unitToPixel(1.5, px(32), 32)) shouldBe container.toArgb()
            shouldBeNear(
                pixels.argbAt(unitToPixel(4.0, px(32), 32), unitToPixel(4.0, px(32), 32)),
                LogoColors.tile.argb32(),
                clue = "inside the arc",
            )
        }

    @Test
    fun `small logo corner is rounded and transparent`() =
        runComposeUiTest {
            setLogo(20.dp)

            val pixels = onNodeWithTag(BOX).pixels()

            // small drawing: tile from 0 with rx 4, arc centre (4; 4); (0.5; 0.5) is outside, (1.5; 1.5) inside
            pixels.argbAt(unitToPixel(0.5, px(20), 16), unitToPixel(0.5, px(20), 16)) shouldBe container.toArgb()
            shouldBeNear(
                pixels.argbAt(unitToPixel(1.5, px(20), 16), unitToPixel(1.5, px(20), 16)),
                LogoColors.tile.argb32(),
                clue = "inside the arc",
            )
        }

    @Test
    fun `logo looks the same in dark and light themes`() =
        runComposeUiTest {
            var variant by mutableStateOf(0)
            setContent {
                Scaled {
                    val colors = if (variant == 1) paperColors() else SampleTokens.colors()
                    val textColor = if (variant == 2) Color.Red else null
                    AmTheme(colors, SampleTokens.shape(), PLEX_SPEC) {
                        CompositionLocalProvider(LocalTextColor provides textColor) {
                            Box(Modifier.testTag(BOX).background(container)) { AmLogo(32.dp, null) }
                        }
                    }
                }
            }
            val dark = onNodeWithTag(BOX).pixels()
            shouldBeNear(dark.argbAt(px(3), px(20)), LogoColors.tile.argb32(), clue = "drawn at all")

            variant = 1
            waitForIdle()
            val light = onNodeWithTag(BOX).pixels()
            variant = 2
            waitForIdle()
            val red = onNodeWithTag(BOX).pixels()

            light.samePixelsAs(dark) shouldBe true
            red.samePixelsAs(dark) shouldBe true
        }

    @Test
    fun `logo reads its description`() =
        runComposeUiTest {
            setLogo(20.dp, description = "Logo Quayside")

            onNodeWithContentDescription("Logo Quayside").assertExists()
            onAllNodesWithContentDescription("Logo Quayside", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `decorative logo exposes nothing to accessibility`() =
        runComposeUiTest {
            setLogo(20.dp, description = null)

            // the unmerged tree has a layout child carrying only the clip shape; no description, text, role or action
            val nodes = onNodeWithTag(BOX, useUnmergedTree = true).fetchSemanticsNode().withDescendants()

            val exposed =
                nodes.flatMap { node -> node.config.map { it.key } }.filter { it in ACCESSIBLE_PROPERTIES }
            exposed.shouldBeEmpty()
        }

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.withDescendants() }

    @Test
    fun `logo is never focused by Tab`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        AmLogo(20.dp, "Logo Quayside")
                        AmIconButton(IconKind.PLUS, "Other", onClick = {})
                    }
                }
            }

            press(Key.Tab)
            onNodeWithContentDescription("Other").assertIsFocused()
            press(Key.Tab)

            val logo = onNodeWithContentDescription("Logo Quayside").fetchSemanticsNode().config
            (logo.getOrElse(SemanticsProperties.Focused) { false }) shouldBe false
            (SemanticsActions.OnClick in logo) shouldBe false
        }

    private fun PixelMap.samePixelsAs(other: PixelMap): Boolean = buffer.contentEquals(other.buffer)

    private companion object {
        const val BOX = "box"
        const val CONTAINER = 0xFF336699L
        val ACCESSIBLE_PROPERTIES: Set<SemanticsPropertyKey<*>> =
            setOf(
                SemanticsProperties.ContentDescription,
                SemanticsProperties.Text,
                SemanticsProperties.EditableText,
                SemanticsProperties.Role,
                SemanticsProperties.Focused,
                SemanticsActions.OnClick,
                SemanticsActions.OnLongClick,
                SemanticsActions.RequestFocus,
            )
    }
}
