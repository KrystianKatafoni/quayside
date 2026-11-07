package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Counters and the selected entry of the global views (AM-816), read from pixels at 4 px per dp. */
@OptIn(ExperimentalTestApi::class)
internal class GlobalViewsLookScreenTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.panel.argb()

    private fun snapshot() =
        freshSnapshot(
            aSession(id = "q1", project = MC_PROJECT.id, state = SessionState.AwaitingInput("q")),
            aSession(id = "q2", project = MC_OTHER.id, state = SessionState.AwaitingInput("q")),
            aSession(id = "q3", project = null, state = SessionState.AwaitingInput("q")),
            aSession(id = "f1", project = MC_PROJECT.id, state = SessionState.Failed),
        )

    private fun description(
        view: GlobalView,
        count: Int,
    ) = mcText("view.count", mcText("view.${view.name.lowercase()}"), "$count")

    @Test
    fun `view count is a counter with description`() =
        runComposeUiTest {
            // Given three waiting sessions, shown in the "Czeka na mnie" entry
            showScripted(snapshot(), scaled = true) { GlobalViews(it) }
            val entry = MissionControlTags.view(GlobalView.WAITING)
            waitUntil {
                onAllNodes(hasContentDescription(description(GlobalView.WAITING, 3)) and inTag(entry), true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // When the counter node is read
            val counter =
                onAllNodes(hasContentDescription(description(GlobalView.WAITING, 3)) and inTag(entry), true).onFirst()
            val bounds = counter.fetchSemanticsNode().boundsInRoot
            val pixels = counter.pixelMap()

            // Then it is announced by its description, at least 22 dp, as wide as high (a circle for one digit),
            // with the corner outside the circle: the first coloured pixel on the diagonal is 0.29 radius in
            bounds.width shouldBe bounds.height
            (bounds.width >= 22 * SCALE) shouldBe true
            pixels.argbAt(0, 0) shouldBe container
            pixels.diagonalOnset(container).shouldNotBeNull().shouldBeInRange(12..14)
            pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.status.warnBg.argb()
        }

    @Test
    fun `selected row has accent frame and row radius`() =
        runComposeUiTest {
            // Given the "Czeka na mnie" view shown
            val controller = showScripted(snapshot(), scaled = true) { GlobalViews(it) }
            awaitTag(MissionControlTags.view(GlobalView.WAITING))
            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING)) }
            waitForIdle()

            // When the entries are drawn
            val selected = awaitTag(MissionControlTags.view(GlobalView.WAITING)).pixelMap()
            val unselected = awaitTag(MissionControlTags.view(GlobalView.FAILED))

            // Then the selected one has the accent frame and the sel fill, the other has neither
            selected.topEdgeColor(container) shouldBe colors.content.accent.argb()
            awaitTag(MissionControlTags.view(GlobalView.WAITING)).argbColors() shouldContain colors.surfaces.sel.argb()
            unselected.argbColors() shouldNotContain colors.content.accent.argb()
            unselected.argbColors() shouldNotContain colors.surfaces.sel.argb()
            // And the frame follows the row radius r + 2 = 10 dp of plex: the corner begins 0.29 radius in
            selected.diagonalOnset(container).shouldNotBeNull().shouldBeInRange(10..12)
        }

    @Test
    fun `clicking a view entry still shows the view`() =
        runComposeUiTest {
            val controller = showScripted(snapshot()) { GlobalViews(it) }

            awaitTag(MissionControlTags.view(GlobalView.FAILED)).performClick()
            waitForIdle()

            controller.global().view shouldBe GlobalView.FAILED
        }
}
