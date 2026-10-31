package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.projects.api.FakeProjectsApi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * UI test of the window on fakes; the empty state itself is tested in `feature:mission-control:ui`. The full
 * functional scenario moves to `tests:functional` (AM-904).
 */
@OptIn(ExperimentalTestApi::class)
class MissionControlEmptyTest {
    @Test
    fun `window shows the sidebar next to Mission Control`() =
        runComposeUiTest {
            // Given the window on fakes with the palette closed (controllers in the composition scope)
            showWindow(FakeProjectsApi())

            // Then Mission Control is on the right of the sidebar and says there are no projects
            waitUntilExactlyOneExists(hasTestTag(MISSION_CONTROL_TAG))
            val sidebar = onNodeWithTag(SIDEBAR_TAG).assertIsDisplayed().getBoundsInRoot()
            val missionControl = onNodeWithTag(MISSION_CONTROL_TAG).assertIsDisplayed().getBoundsInRoot()
            (sidebar.right <= missionControl.left) shouldBe true
            onAllNodes(hasText("Brak projektów") and hasAnyAncestor(hasTestTag(MISSION_CONTROL_TAG)))
                .onFirst()
                .assertIsDisplayed()
            // And the palette is not shown until it is opened
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }

    private companion object {
        /** `ProjectsTags.SIDEBAR`, internal to `feature:projects:ui`. */
        const val SIDEBAR_TAG = "projects.sidebar"

        /** `MissionControlTags.ROOT`, internal to `feature:mission-control:ui`. */
        const val MISSION_CONTROL_TAG = "missionControl"
    }
}
