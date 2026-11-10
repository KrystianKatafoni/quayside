package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The chips of the group overview (FR-P6, FR-P8, AM-310): waiting, working, failed in that order, none for a state at
 * zero or for finished sessions, no old "brak agentów", and no row for the main folder. Subfolders are `bob`, `Carl`,
 * `Anna`.
 */
@OptIn(ExperimentalTestApi::class)
internal class GroupOverviewCountersScreenTest {
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
    fun `overview chips keep the order waiting working failed and have none for done`() =
        runComposeUiTest {
            // Given a subfolder with 2 waiting, 1 working, 3 done and 4 failed sessions
            showGroup(carlSessions())
            overviewRow("Carl")

            // Then the chips are announced in that order, without one for the 3 done sessions ...
            val expected = listOf("czeka: 2", "pracuje: 1", "błąd: 4")
            counterTexts(carl) shouldBe expected
            // ... and drawn from left to right in that order, each in its own place
            val lefts = expected.map { counterNode(carl, it).fetchSemanticsNode().boundsInRoot.left }
            lefts shouldBe lefts.sorted()
            lefts.toSet().size shouldBe 3
            onAllNodes(hasContentDescription("gotowe", substring = true) and inTag(carl), useUnmergedTree = true)
                .assertCountEquals(0)
        }

    @Test
    fun `row without sessions has no chips and no idle text`() =
        runComposeUiTest {
            // Given a group where only Anna has a session, and Carl only finished ones
            showGroup(
                listOf(
                    groupSession("a", "Anna", SessionState.Working, 1),
                    groupSession("c", "Carl", SessionState.Done, 2),
                    groupSession("c2", "Carl", SessionState.Stopped, 3),
                ),
            )
            overviewRow("Anna")

            // Then the quiet rows have no chip at all and no text "brak agentów" is shown anywhere
            counterTexts(carl) shouldBe emptyList()
            counterTexts(MissionControlTags.overviewRow(sub("bob"))) shouldBe emptyList()
            counterTexts(MissionControlTags.overviewRow(sub("Anna"))) shouldBe listOf("pracuje: 1")
            onAllNodes(hasText("brak agentów", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `badge says folder główny while the overview has no such row`() =
        runComposeUiTest {
            // Given a session in the project directory, so that its badge and the overview are on screen together
            showGroup(listOf(groupSession("rooted", "", SessionState.Working, 1)))
            awaitTag(MissionControlTags.OVERVIEW)
            awaitTag(MissionControlTags.row(SessionId("rooted")))

            // Then the badge says "folder główny" (unmerged tree: it is read by its description) ...
            mcText("row.place.root") shouldBe "folder główny"
            onAllNodes(
                hasText("folder główny") and inTag(MissionControlTags.rowPlace(SessionId("rooted"))),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size shouldBe 1
            // ... and the overview has no such row, nor the old name "Pozostałe"
            onAllNodes(hasText("folder główny") and inTag(MissionControlTags.OVERVIEW), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodes(hasText("Pozostałe"), useUnmergedTree = true).assertCountEquals(0)
        }
}
