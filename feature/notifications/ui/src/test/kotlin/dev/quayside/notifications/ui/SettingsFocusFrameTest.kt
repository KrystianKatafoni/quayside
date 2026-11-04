package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/** Pixels from the outer edge of the frame (2 dp wide), 1 px inside it; the layout is 1 dp = 1 px. */
private const val FRAME_OUTER_PX = 3

/** The focus frame of a [ToggleSetting] (NFR-6, AM-806): `amFocusFrame` in the colour `text` around its node. */
@OptIn(ExperimentalTestApi::class)
class SettingsFocusFrameTest {
    private val frame =
        SampleTokens
            .colors()
            .content.text
            .toColor()
            .toArgb()

    /**
     * ARGB of the root pixel in the outer 2 dp of the left edge of the control [tag], at half of its height. The
     * frame is drawn in the 4 dp around the tagged node, so the node alone is not a proof; the whole root is captured.
     */
    private fun ComposeUiTest.edgePixel(tag: String): Int {
        val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        return image[bounds.left.toInt() - FRAME_OUTER_PX, ((bounds.top + bounds.bottom) / 2).toInt()].toArgb()
    }

    @Test
    fun `focused control shows the focus frame`() =
        runComposeUiTest {
            // Given two switches, none focused
            setContent {
                AmTestTheme {
                    Column {
                        ToggleSetting(SettingsTags.POPUPS, "settings.popups", on = true) {}
                        ToggleSetting(SettingsTags.VOICE, "settings.voice", on = false) {}
                    }
                }
            }
            waitUntilExactlyOneExists(hasTestTag(SettingsTags.POPUPS), WAIT_MS)
            edgePixel(SettingsTags.POPUPS) shouldNotBe frame

            // When the first switch gets the focus
            onNodeWithTag(SettingsTags.POPUPS).requestFocus()
            waitUntilExactlyOneExists(hasTestTag(SettingsTags.POPUPS) and isFocused(), WAIT_MS)
            waitForIdle()

            // Then its edge is drawn in the colour text, and the other switch has no frame
            edgePixel(SettingsTags.POPUPS) shouldBe frame
            edgePixel(SettingsTags.VOICE) shouldNotBe frame
        }
}
