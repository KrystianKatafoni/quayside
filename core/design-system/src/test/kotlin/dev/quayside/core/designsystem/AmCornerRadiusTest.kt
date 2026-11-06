package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Which derived radius a component uses, read from the corner pixels at 4 px per dp (AM-816): stat card and panel
 * card (r+4), icon tile and list row (r+2); r = 0 stays square. A pixel outside the expected arc shows the container,
 * a pixel inside it shows the fill, so a larger or a smaller radius than the expected one fails.
 */
@OptIn(ExperimentalTestApi::class)
class AmCornerRadiusTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()

    private fun ComposeUiTest.setThemed(
        radius: Int,
        content: @Composable () -> Unit,
    ) = setContent {
        Scaled {
            AmTheme(colors, SampleTokens.shape(radius), PLEX_SPEC) {
                Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) { content() }
            }
        }
    }

    /** Checks the corner of [this] that starts [offsetPx] pixels from the box edge. */
    private fun PixelMap.assertCorner(
        radiusDp: Int,
        fill: Int,
        offsetPx: Int = 0,
    ) {
        if (radiusDp == 0) {
            // square: the very corner is drawn (border or fill), never the container
            argbAt(offsetPx, offsetPx) shouldNotBe container
            argbAt(offsetPx + px(2), offsetPx + px(2)) shouldBe fill
        } else {
            argbAt(offsetPx + outsideArcPixel(radiusDp), offsetPx + outsideArcPixel(radiusDp)) shouldBe container
            argbAt(offsetPx + insideArcPixel(radiusDp), offsetPx + insideArcPixel(radiusDp)) shouldBe fill
        }
    }

    private fun expected(
        radius: Int,
        extra: Int,
    ) = if (radius == 0) 0 else radius + extra

    @ParameterizedTest(name = "r {0}")
    @ValueSource(ints = [0, 8, 16])
    fun `stat card corner uses card radius`(radius: Int) =
        runComposeUiTest {
            setThemed(radius) { AmStatCard("12", "Waiting", IconKind.WAITING) }

            onNodeWithTag(BOX).pixels().assertCorner(expected(radius, CARD), colors.surfaces.panel.argb32())
        }

    @ParameterizedTest(name = "r {0}")
    @ValueSource(ints = [0, 8, 16])
    fun `panel corner uses card radius`(radius: Int) =
        runComposeUiTest {
            setThemed(radius) { AmPanel(Modifier.size(100.dp, 60.dp)) {} }

            onNodeWithTag(BOX).pixels().assertCorner(expected(radius, CARD), colors.surfaces.panel.argb32())
        }

    @ParameterizedTest(name = "r {0}")
    @ValueSource(ints = [0, 8, 16])
    fun `icon tile corner uses tile radius`(radius: Int) =
        runComposeUiTest {
            setThemed(radius) { AmIconTile(IconKind.WAITING, StatusTone.OK, null, Modifier.size(48.dp)) }

            onNodeWithTag(BOX)
                .pixels()
                .assertCorner(expected(radius, TILE), toneBackground(StatusTone.OK, colors).argb32())
        }

    @ParameterizedTest(name = "r {0}")
    @ValueSource(ints = [0, 8, 16])
    fun `selected list row corner uses row radius`(radius: Int) =
        runComposeUiTest {
            // The row reserves 4 dp for its focus frame around the element
            setThemed(radius) { AmListRow(true, {}, Modifier.size(120.dp, 60.dp)) { AmText("Row") } }

            onNodeWithTag(BOX)
                .pixels()
                .assertCorner(expected(radius, ROW), colors.surfaces.sel.argb32(), offsetPx = px(FRAME_DP))
        }

    private companion object {
        const val BOX = "box"
        const val CARD = 4
        const val TILE = 2
        const val ROW = 2
        const val FRAME_DP = 4
    }
}
