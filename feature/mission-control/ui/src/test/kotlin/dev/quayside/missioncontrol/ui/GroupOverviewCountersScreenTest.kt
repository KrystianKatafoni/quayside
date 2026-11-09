package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The four counters of the group overview (FR-P6, FR-P8, NFR-6, AM-310): order waiting, working, done, failed, zero
 * counters instead of the old "brak agentów", the root row "folder główny" named like the badge in the list, and the
 * ring of a zero counter read from pixels at 4 px per dp. Subfolders are `bob`, `Carl`, `Anna`.
 */
@OptIn(ExperimentalTestApi::class)
internal class GroupOverviewCountersScreenTest {
    private val colors = SampleTokens.colors()
    private val carl = MissionControlTags.overviewRow(sub("Carl"))

    private fun carlSessions() =
        listOf(
            groupSession("w1", "Carl", SessionState.AwaitingInput("q"), 1),
            groupSession("w2", "Carl", SessionState.AwaitingPermission("Bash"), 2),
            groupSession("k1", "Carl", SessionState.Working, 3),
            groupSession("d1", "Carl", SessionState.Done, 4),
            groupSession("d2", "Carl/x", SessionState.Done, 5),
            groupSession("d3", "Carl", SessionState.Done, 6),
            groupSession("f1", "Carl", SessionState.Failed, 7),
            groupSession("f2", "Carl", SessionState.Failed, 8),
            groupSession("f3", "Carl", SessionState.Failed, 9),
            groupSession("f4", "Carl", SessionState.Failed, 10),
        )

    @Test
    fun `overview counters keep the order waiting working done failed`() =
        runComposeUiTest {
            // Given a subfolder with 2 waiting, 1 working, 3 done and 4 failed sessions
            showGroup(carlSessions())
            overviewRow("Carl")

            // Then the counters are announced in that order ...
            val expected = listOf("czeka: 2", "pracuje: 1", "gotowe: 3", "błąd: 4")
            counterTexts(carl) shouldBe expected
            // ... and drawn from left to right in that order
            val lefts = expected.map { counterNode(carl, it).fetchSemanticsNode().boundsInRoot.left }
            lefts shouldBe lefts.sorted()
            lefts.toSet().size shouldBe 4
        }

    @Test
    fun `overview shows four zero counters instead of an idle text`() =
        runComposeUiTest {
            // Given a group where only Anna has a session
            showGroup(listOf(groupSession("a", "Anna", SessionState.Working, 1)))
            overviewRow("Anna")

            // Then the quiet rows have four zero counters and no text "brak agentów" is shown anywhere
            val zeros = listOf("czeka: 0", "pracuje: 0", "gotowe: 0", "błąd: 0")
            counterTexts(carl) shouldBe zeros
            counterTexts(MissionControlTags.overviewRow(sub("bob"))) shouldBe zeros
            counterTexts(MissionControlTags.OVERVIEW_ROOT) shouldBe zeros
            onAllNodes(hasText("brak agentów", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `root row is labelled folder główny like the badge`() =
        runComposeUiTest {
            // Given a session in the project directory, so that its badge and the root row are on screen together
            showGroup(listOf(groupSession("rooted", "", SessionState.Working, 1)))
            awaitTag(MissionControlTags.OVERVIEW_ROOT)
            awaitTag(MissionControlTags.row(SessionId("rooted")))

            // Then the root row and the badge carry the same text, from the same key
            val label = mcText("overview.root")
            label shouldBe mcText("row.place.root")
            // (unmerged tree: the badge clears the semantics of its text and is read by its description)
            listOf(MissionControlTags.OVERVIEW_ROOT, MissionControlTags.rowPlace(SessionId("rooted"))).forEach {
                onAllNodes(hasText(label) and inTag(it), useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1
            }
            // and the old name of the row is gone
            onAllNodes(hasText("Pozostałe"), useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `root row click does nothing`() =
        runComposeUiTest {
            val clicks = mutableListOf<ProjectFocus>()
            showGroup(carlSessions(), MissionControlHandlers(onOpenSubfolder = { clicks += it }))

            awaitTag(MissionControlTags.OVERVIEW_ROOT).performClick()
            waitForIdle()

            clicks shouldBe emptyList()
            exists(MissionControlTags.OVERVIEW) shouldBe true
        }

    private fun androidx.compose.ui.test.ComposeUiTest.showZeroOverview() =
        setContent {
            AmTestTheme {
                Scaled4 {
                    Box(Modifier.testTag(WRAP).background(colors.surfaces.bg.toColor())) {
                        GroupOverviewView(
                            GroupOverview(
                                project = OVERVIEW_GROUP.id,
                                rows = listOf(SubfolderStats(sub("Anna"), SessionStats())),
                                rest = SessionStats(),
                            ),
                            onEvent = {},
                        )
                    }
                }
            }
        }

    private fun ring(tone: StatusTone): Rgba =
        when (tone) {
            StatusTone.WARN -> colors.status.warn
            StatusTone.WORK -> colors.status.work
            StatusTone.OK -> colors.status.ok
            else -> colors.status.err
        }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("zeroCounters")
    fun `zero counters in the overview use the ring and muted number`(
        row: String,
        description: String,
        tone: StatusTone,
    ) = runComposeUiTest {
        // Given an overview whose rows have only zero counters
        showZeroOverview()
        awaitTag(WRAP)

        // When a zero counter is drawn
        val counter = counterNode(row, description)
        val pixels = counter.pixelMap()

        // Then it has a 1 dp ring in the status token, the panel inside it, and a muted number
        pixels.argbAt(2, pixels.height / 2) shouldBe ring(tone).argb()
        pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.surfaces.panel.argb()
        counter.argbColors() shouldContain colors.content.muted.argb()
        counter.argbColors() shouldNotContain colors.content.text.argb()
    }

    companion object {
        private const val WRAP = "overview.counters.wrap"

        @JvmStatic
        fun zeroCounters(): List<Arguments> =
            listOf(MissionControlTags.overviewRow(sub("Anna")), MissionControlTags.OVERVIEW_ROOT).flatMap { row ->
                listOf(
                    "czeka: 0" to StatusTone.WARN,
                    "pracuje: 0" to StatusTone.WORK,
                    "gotowe: 0" to StatusTone.OK,
                    "błąd: 0" to StatusTone.ERR,
                ).map { (text, tone) -> arguments(row, text, tone) }
            }
    }
}
