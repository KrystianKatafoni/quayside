package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Header, stat cards and filters of the project page (plan AM-809). */
@OptIn(ExperimentalTestApi::class)
internal class ProjectPageScreenTest {
    private fun sessions() =
        fakeSessions(
            worldSession("q1", SessionState.AwaitingInput("q"), index = 1),
            worldSession("q2", SessionState.AwaitingPermission("Bash"), index = 2),
            worldSession("w1", SessionState.Working, index = 3),
            worldSession("d1", SessionState.Done, index = 4),
            worldSession("d2", SessionState.Done, index = 5),
            worldSession("d3", SessionState.Done, index = 6),
            worldSession("f1", SessionState.Failed, index = 7),
            worldSession("f2", SessionState.Failed, index = 8),
            worldSession("f3", SessionState.Failed, index = 9),
            worldSession("f4", SessionState.Failed, index = 10),
            worldSession("s1", SessionState.Stopped, index = 11),
        )

    @Test
    fun `project page shows four stat cards with counts`() =
        runComposeUiTest {
            // Given a project with a different number of sessions in each group, and one stopped
            showMc(sessions())

            // Then each card shows its own count and label (the stopped session is in no card)
            awaitTag(MissionControlTags.stat("waiting")).assertTextEquals("2", mcText("stat.waiting"))
            awaitTag(MissionControlTags.stat("working")).assertTextEquals("1", mcText("stat.working"))
            awaitTag(MissionControlTags.stat("done")).assertTextEquals("3", mcText("stat.done"))
            awaitTag(MissionControlTags.stat("failed")).assertTextEquals("4", mcText("stat.failed"))
        }

    @Test
    fun `stat cards count the whole project whatever the filter`() =
        runComposeUiTest {
            showMc(sessions())
            awaitTag(MissionControlTags.filter(SessionFilter.FINISHED)).performClick()

            awaitTag(MissionControlTags.stat("working")).assertTextEquals("1", mcText("stat.working"))
            awaitTag(MissionControlTags.stat("done")).assertTextEquals("3", mcText("stat.done"))
        }

    @Test
    fun `global screen has no stats`() =
        runComposeUiTest {
            // Given the project page with its cards
            val controller = showMc(sessions())
            awaitTag(MissionControlTags.stat("waiting"))

            // When a global view is shown
            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING)) }
            waitForIdle()

            // Then there are no cards
            awaitTag(MissionControlTags.HEADER)
            listOf("waiting", "working", "done", "failed").forEach {
                hasTag(MissionControlTags.stat(it)) shouldBe false
            }
        }

    @Test
    fun `filters are segments that never take focus`() =
        runComposeUiTest {
            showMc(sessions())
            val all = awaitTag(MissionControlTags.filter(SessionFilter.ALL))
            val active = awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE))
            all.assertIsSelected()
            active.assertIsNotSelected()

            // When the user clicks "Aktywne"
            active.performClick()

            // Then it is the selected segment and nothing took focus
            awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE)).assertIsSelected()
            awaitTag(MissionControlTags.filter(SessionFilter.ALL)).assertIsNotSelected()
            awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE)).assertIsNotFocused()
        }
}
