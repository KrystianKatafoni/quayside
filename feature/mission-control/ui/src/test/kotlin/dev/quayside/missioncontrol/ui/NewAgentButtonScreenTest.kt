package dev.quayside.missioncontrol.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The "Nowy agent" button in the title row of the project page and of the global pages: on the title's line, at the
 * right edge of the header, and only when the handlers carry a `newAgent` action.
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentButtonScreenTest {
    private fun sessions() = fakeSessions(worldSession("w1", index = 1))

    private fun handlers(
        clicks: AtomicInteger = AtomicInteger(),
        enabled: Boolean = true,
        keyCap: String? = "⌘N",
    ) = MissionControlHandlers(newAgent = NewAgentAction({ clicks.incrementAndGet() }, enabled, keyCap))

    private fun ComposeUiTest.showProject(handlers: MissionControlHandlers) {
        showMc(sessions(), handlers)
        awaitTag(MissionControlTags.HEADER)
    }

    private fun ComposeUiTest.showGlobal(handlers: MissionControlHandlers) {
        showMc(sessions(), handlers).onIntent(MissionControlIntent.ShowView(GlobalView.WORKING))
        waitUntil { textUnder(MissionControlTags.HEADER, mcText("view.working")).isDisplayedSafely() }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = awaitTag(tag).fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.isDisplayedSafely() = runCatching { assertExists() }.isSuccess

    private fun ComposeUiTest.titleBounds(title: String): Rect =
        textUnder(MissionControlTags.HEADER, title).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.assertOnTitleLineAtRightEdge(title: String) {
        val button = bounds(MissionControlTags.NEW_AGENT_BUTTON)
        val header = bounds(MissionControlTags.HEADER)
        val text = titleBounds(title)

        // the same line: the vertical centres of the title and of the button meet
        button.center.y.shouldBe(text.center.y.plusOrMinus(3f))
        // the right edge of the button is the right edge of the header, and the title stays left of it
        button.right.shouldBe(header.right.plusOrMinus(2f))
        (text.right <= button.left) shouldBe true
    }

    @Test
    fun `project page has the button on the title line at the right edge`() =
        runComposeUiTest {
            showProject(handlers())

            assertOnTitleLineAtRightEdge("Quayside")
        }

    @Test
    fun `global page has the button on the title line at the right edge`() =
        runComposeUiTest {
            showGlobal(handlers())

            assertOnTitleLineAtRightEdge(mcText("view.working"))
        }

    @Test
    fun `project page without a newAgent handler has no button`() =
        runComposeUiTest {
            showProject(MissionControlHandlers(keyCaps = SessionKeyCaps("⏎", "r", "x", "j", "k", "␣")))

            hasTag(MissionControlTags.NEW_AGENT_BUTTON) shouldBe false
            onAllNodes(hasText("Nowy agent", substring = true)).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `global page without a newAgent handler has no button`() =
        runComposeUiTest {
            showGlobal(MissionControlHandlers())

            hasTag(MissionControlTags.NEW_AGENT_BUTTON) shouldBe false
        }

    @Test
    fun `a click on the project page button calls onClick once`() =
        runComposeUiTest {
            val clicks = AtomicInteger()
            showProject(handlers(clicks))

            awaitTag(MissionControlTags.NEW_AGENT_BUTTON).performClick()

            clicks.get() shouldBe 1
        }

    @Test
    fun `a click on the global page button calls onClick once`() =
        runComposeUiTest {
            val clicks = AtomicInteger()
            showGlobal(handlers(clicks))

            awaitTag(MissionControlTags.NEW_AGENT_BUTTON).performClick()

            clicks.get() shouldBe 1
        }

    @Test
    fun `a disabled button does not call onClick`() =
        runComposeUiTest {
            val clicks = AtomicInteger()
            showProject(handlers(clicks, enabled = false))

            awaitTag(MissionControlTags.NEW_AGENT_BUTTON).assertIsNotEnabled()
            awaitTag(MissionControlTags.NEW_AGENT_BUTTON).performClick()

            clicks.get() shouldBe 0
        }

    @Test
    fun `an enabled button is enabled`() =
        runComposeUiTest {
            showProject(handlers())

            awaitTag(MissionControlTags.NEW_AGENT_BUTTON).assertIsEnabled()
        }

    @Test
    fun `the button shows its label and the shortcut from keyCap`() =
        runComposeUiTest {
            showProject(handlers(keyCap = "⌘N"))

            awaitTag(MissionControlTags.NEW_AGENT_BUTTON)
            textUnder(MissionControlTags.NEW_AGENT_BUTTON, "Nowy agent").assertExists()
            textUnder(MissionControlTags.NEW_AGENT_BUTTON, "⌘N").assertExists()
        }

    @Test
    fun `the button shows another keyCap as given and no shortcut without one`() =
        runComposeUiTest {
            showProject(handlers(keyCap = "⌥X"))
            awaitTag(MissionControlTags.NEW_AGENT_BUTTON)
            textUnder(MissionControlTags.NEW_AGENT_BUTTON, "⌥X").assertExists()
            onAllNodes(hasText("⌘N", substring = true)).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `the button without keyCap shows no shortcut`() =
        runComposeUiTest {
            showProject(handlers(keyCap = null))
            awaitTag(MissionControlTags.NEW_AGENT_BUTTON)

            onAllNodes(hasText("⌘", substring = true)).fetchSemanticsNodes().size shouldBe 0
        }
}
