package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * The focus frame of the buttons of the "Nowy agent" dialog (NFR-6, AM-806): `amFocusFrame` in the colour `text`, seen
 * on a capture of the whole dialog (the frame is drawn at the edge of the button, so the button alone is not a proof).
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentFocusFrameTest {
    private val rig = NewAgentRig(RecordingCommands(NewAgentWorld.sessions()))
    private val frame =
        SampleTokens
            .colors()
            .content.text
            .toColor()
            .toArgb()

    private fun ComposeUiTest.shown() {
        showNewAgent(rig)
        openNewAgent(rig)
    }

    /** ARGB of the dialog pixel 1 px inside the left edge of the node [tag], at half of its height. */
    private fun ComposeUiTest.edgePixel(tag: String): Int {
        val dialog = onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG)
        val button = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val origin = dialog.fetchSemanticsNode().boundsInRoot
        val x = (button.left - origin.left).toInt() + 1
        val y = ((button.top + button.bottom) / 2 - origin.top).toInt()
        return dialog.captureToImage().toPixelMap()[x, y].toArgb()
    }

    @Test
    fun `focused control shows the focus frame`() =
        runComposeUiTest {
            // Given the open dialog; the focus starts on the prompt, so Cancel has no frame
            shown()
            edgePixel(MissionControlTags.NEW_AGENT_CANCEL) shouldNotBe frame

            // When Cancel gets the focus
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).requestFocus()
            waitUntilExactlyOneExists(hasTestTag(MissionControlTags.NEW_AGENT_CANCEL) and isFocused(), FOCUS_WAIT_MS)
            waitForIdle()

            // Then its edge is drawn in the colour text
            edgePixel(MissionControlTags.NEW_AGENT_CANCEL) shouldBe frame
        }

    @Test
    fun `focus frame keeps layout when focus changes`() =
        runComposeUiTest {
            // Given the focus on Cancel, and the sizes of Start and of the dialog
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).requestFocus()
            waitForIdle()
            val before = onNodeWithTag(MissionControlTags.NEW_AGENT_START).fetchSemanticsNode().size
            val dialogBefore = onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).fetchSemanticsNode().size

            // When Tab moves the focus to Start
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).press(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_START)

            // Then neither the button nor the dialog changed size
            onNodeWithTag(MissionControlTags.NEW_AGENT_START).fetchSemanticsNode().size shouldBe before
            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).fetchSemanticsNode().size shouldBe dialogBefore
        }
}
