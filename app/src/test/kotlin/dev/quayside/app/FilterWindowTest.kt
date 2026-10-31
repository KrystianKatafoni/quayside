package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import org.junit.jupiter.api.Test

/** The Mission Control filter from the palette in the whole window (FR-K1, FR-S9; AM-409 question 1). */
@OptIn(ExperimentalTestApi::class)
class FilterWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

    @Test
    fun `FR-K1 FR-S9 palette filter Aktywne hides finished sessions`() =
        runComposeUiTest {
            // Given a selected project with a working and a finished session
            val sessions =
                FakeSessions(
                    SessionsWorld(
                        listOf(WorldProject(agent.id, agent.dir)),
                        listOf(
                            WorldSession(SessionId("working-one"), A_PROJECT_DIR, SessionState.Working, startedAt = T0),
                            WorldSession(SessionId("done-one"), A_PROJECT_DIR, SessionState.Done, startedAt = T0),
                        ),
                    ),
                )
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("working-one"))
            await(WindowTags.missionRow("done-one"))

            // When the user opens the palette, searches "Aktywne" and picks "Filtr: Aktywne"
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Aktywne")
            await(WindowTags.paletteRow("sessions.filter.active")).performClick()

            // Then the finished session is hidden and the working one stays
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow("done-one")))
            onNodeWithTag(WindowTags.missionRow("working-one")).assertExists()
        }
}
