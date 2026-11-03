package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.ShapeTokens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The shadow of [AmPanel] (FR-A7), seen from outside: a 60 dp panel sits in a 120 dp white frame with 30 dp of margin
 * on every side, so the pixels just below its bottom edge belong to the frame and show the shadow (or not).
 */
@OptIn(ExperimentalTestApi::class)
class AmPanelShadowTest {
    private val white = Color.White.toArgb()

    private fun shape(shadow: ShadowSpec?): ShapeTokens =
        ShapeTokens(radius = 8, borderWidth = 1, shadow = shadow, backdrop = Backdrop.OPAQUE)

    private fun ComposeUiTest.setPanel(shadow: ShadowSpec?) =
        setContent {
            AmTheme(SampleTokens.colors(), shape(shadow), PLEX_SPEC) {
                Box(
                    Modifier
                        .testTag(FRAME)
                        .size(120.dp)
                        .background(Color.White)
                        .padding(30.dp),
                ) {
                    AmPanel(Modifier.size(60.dp)) {}
                }
            }
        }

    private fun ComposeUiTest.px(dp: Int): Int = (dp * density.density).toInt()

    private fun PixelMap.below(frame: ComposeUiTest): Int = argbAt(width / 2, frame.px(BELOW_EDGE_DP))

    @Test
    fun `panel with shadow darkens pixels below it`() =
        runComposeUiTest {
            // Given a panel with a 50 % black shadow shifted 4 dp down with 12 dp of blur
            setPanel(ShadowSpec(offsetY = 4, blur = 12, spread = 0, color = Rgba.BLACK.withAlpha(50)))

            // When the frame is rendered
            val pixels = onNodeWithTag(FRAME).pixels()

            // Then the frame just below the panel is darker than white, and its far corner is untouched
            val below = pixels.below(this)
            (Color(below).red < Color.White.red) shouldBe true
            Color(below).red shouldBe Color(below).green
            pixels.argbAt(px(2), px(2)) shouldBe white
            pixels.argbAt(pixels.width - px(2), pixels.height - px(2)) shouldBe white
        }

    @Test
    fun `panel without shadow leaves the background below it`() =
        runComposeUiTest {
            // Given a panel with no shadow
            setPanel(shadow = null)

            // When the frame is rendered
            val pixels = onNodeWithTag(FRAME).pixels()

            // Then every pixel outside the panel is the plain white frame
            pixels.below(this) shouldBe white
            pixels.argbAt(px(2), px(2)) shouldBe white
            pixels.argbAt(pixels.width / 2, px(28)) shouldBe white
        }

    @Test
    fun `positive offset moves the shadow down so it is darker below the panel than above`() =
        runComposeUiTest {
            // Given a panel with a black shadow shifted 6 dp down
            setPanel(ShadowSpec(offsetY = 6, blur = 8, spread = 0, color = Rgba.BLACK.withAlpha(60)))

            // When the frame is rendered
            val pixels = onNodeWithTag(FRAME).pixels()

            // Then the pixel 2 dp under the panel is darker than the one 2 dp over it
            val above = Color(pixels.argbAt(pixels.width / 2, px(ABOVE_EDGE_DP)))
            val below = Color(pixels.below(this))
            (below.red < above.red) shouldBe true
        }

    @Test
    fun `coloured shadow tints the pixels below the panel`() =
        runComposeUiTest {
            // Given a panel with a violet shadow (low green channel)
            setPanel(ShadowSpec(offsetY = 4, blur = 12, spread = 0, color = Rgba(0xFF8B5CF6L).withAlpha(60)))

            // When the frame is rendered
            val below = Color(onNodeWithTag(FRAME).pixels().below(this))

            // Then the shadow is violet: green is pulled down further than red
            (below.green < below.red) shouldBe true
        }

    private companion object {
        const val FRAME = "frame"

        /** 2 dp below the panel bottom edge (30 + 60 = 90 dp). */
        const val BELOW_EDGE_DP = 92

        /** 2 dp above the panel top edge (30 dp). */
        const val ABOVE_EDGE_DP = 28
    }
}
