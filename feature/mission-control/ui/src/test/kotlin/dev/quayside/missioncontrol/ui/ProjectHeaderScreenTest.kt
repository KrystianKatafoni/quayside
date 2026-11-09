package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The two-row header of the project page (AM-816): title, then path, count and the filter segments on the right. */
@OptIn(ExperimentalTestApi::class)
internal class ProjectHeaderScreenTest {
    private val colors = SampleTokens.colors()

    private fun sessions() =
        fakeSessions(
            worldSession("q1", SessionState.AwaitingInput("q"), index = 1),
            worldSession("w1", SessionState.Working, index = 2),
        )

    @Test
    fun `filters sit in the header row after the count`() =
        runComposeUiTest {
            showMc(sessions())

            val title = textUnder(MissionControlTags.HEADER, "Quayside").fetchSemanticsNode().boundsInRoot
            val path = awaitTag(MissionControlTags.HEADER_PATH).fetchSemanticsNode().boundsInRoot
            val count = awaitTag(MissionControlTags.HEADER_COUNT).fetchSemanticsNode().boundsInRoot
            val all = awaitTag(MissionControlTags.filter(SessionFilter.ALL)).fetchSemanticsNode().boundsInRoot
            val finished = awaitTag(MissionControlTags.filter(SessionFilter.FINISHED)).fetchSemanticsNode().boundsInRoot
            val failed = awaitTag(MissionControlTags.stat("failed")).fetchSemanticsNode().boundsInRoot

            // the title is the first row; path, count and filters share the second, above the cards
            title.bottom shouldBeLessThanOrEqualTo path.top + 1f
            all.left shouldBeGreaterThanOrEqualTo count.right
            (all.center.y in count.top..count.bottom) shouldBe true
            (path.center.y in count.top..count.bottom) shouldBe true
            all.bottom shouldBeLessThanOrEqualTo failed.top
            // the segments follow one another and the last one ends at the right edge of the cards
            finished.left shouldBeGreaterThanOrEqualTo all.right
            finished.right.shouldBe(failed.right.plusOrMinus(2f))
        }

    @Test
    fun `header has no new agent button`() =
        runComposeUiTest {
            showMc(sessions(), MissionControlHandlers(keyCaps = SessionKeyCaps("⏎", "r", "x", "j", "k", "␣")))
            awaitTag(MissionControlTags.HEADER)

            onAllNodes(hasText("Nowy agent", substring = true)).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `header shows path chip and session count`() =
        runComposeUiTest {
            showMc(sessions())

            awaitTag(MissionControlTags.HEADER_PATH)
            textUnder(MissionControlTags.HEADER_PATH, MC_PROJECT.dir.toString()).assertExists()
            awaitTag(MissionControlTags.HEADER_COUNT)
            textUnder(MissionControlTags.HEADER_COUNT, mcText("header.sessions", "2")).assertExists()
        }

    @Test
    fun `selected segment is drawn on sel and the others on raised`() =
        runComposeUiTest {
            showMc(sessions())

            val selected = awaitTag(MissionControlTags.filter(SessionFilter.ALL)).argbColors()
            val other = awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE)).argbColors()

            selected shouldContain colors.surfaces.sel.argb()
            other shouldContain colors.surfaces.raised.argb()
            other shouldNotContain colors.surfaces.sel.argb()
        }

    @Test
    fun `clicking a segment still filters`() =
        runComposeUiTest {
            showMc(sessions())

            awaitTag(MissionControlTags.filter(SessionFilter.FINISHED)).performClick()
            waitForIdle()

            hasTag(
                MissionControlTags.row(
                    dev.quayside.core.domain
                        .SessionId("q1"),
                ),
            ) shouldBe false
        }

    private fun ComposeUiTest.showHeader(branch: String?) {
        setContent {
            AmTestTheme {
                ProjectHeaderView(ProjectHeader("Quayside", MC_PROJECT.dir, 2, branch), SessionFilter.ALL) {}
            }
        }
    }

    @Test
    fun `header shows the branch chip between the path and the count`() =
        runComposeUiTest {
            showHeader("feat/AM-818")

            val path = awaitTag(MissionControlTags.HEADER_PATH).fetchSemanticsNode().boundsInRoot
            val branch = awaitTag(MissionControlTags.HEADER_BRANCH).fetchSemanticsNode().boundsInRoot
            val count = awaitTag(MissionControlTags.HEADER_COUNT).fetchSemanticsNode().boundsInRoot
            textUnder(MissionControlTags.HEADER_BRANCH, "feat/AM-818").assertExists()
            branch.left shouldBeGreaterThanOrEqualTo path.right
            count.left shouldBeGreaterThanOrEqualTo branch.right
        }

    @Test
    fun `header without a branch has no branch chip`() =
        runComposeUiTest {
            showHeader(null)
            awaitTag(MissionControlTags.HEADER_COUNT)

            onAllNodesWithTag(MissionControlTags.HEADER_BRANCH).fetchSemanticsNodes().size shouldBe 0
        }
}
