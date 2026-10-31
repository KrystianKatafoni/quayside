package dev.agentmanager.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Global views in the whole window on fakes (FR-S9, FR-K1, FR-K2; AM-407): the entries under the sidebar, the
 * palette commands "Widok: …" and the keys in a view. Session ids equal their names.
 */
@OptIn(ExperimentalTestApi::class)
class GlobalViewsWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    private val elsewhere: Path = Path.of("/Users/test/elsewhere")

    private fun session(
        name: String,
        startedAtSecond: Long,
        state: SessionState,
        cwd: Path,
    ): WorldSession =
        WorldSession(SessionId(name), cwd, state, name = name, startedAt = T0.plusSeconds(startedAtSecond))

    private fun world(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(agent.id, agent.dir), WorldProject(other.id, other.dir)),
                sessions =
                    listOf(
                        session("mine-work", 0, SessionState.Working, A_PROJECT_DIR),
                        session("other-work", 1, SessionState.Working, other.dir),
                        session("none-work", 2, SessionState.Working, elsewhere),
                        session("other-broken", 3, SessionState.Failed, other.dir),
                        session("mine-ask", 4, SessionState.AwaitingInput("q"), A_PROJECT_DIR),
                    ),
            ),
        )

    private fun ComposeUiTest.show(fake: FakeSessions = world()) =
        showWindow(FakeProjectsApi(initial = listOf(agent, other)), fake)

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.gone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    @Test
    fun `FR-S9 clicking a global view clears the project in the sidebar`() =
        runComposeUiTest {
            // Given a selected project
            show()
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(WindowTags.missionRow("mine-work"))
            gone(WindowTags.missionRow("other-work"))

            // When the user clicks "Pracuje" in the left column
            await(WindowTags.view("working")).performClick()

            // Then the sessions of every project are listed and no project is highlighted in the sidebar
            await(WindowTags.missionRow("other-work"))
            await(WindowTags.missionRow("none-work"))
            await(WindowTags.missionRow("mine-work"))
            gone(WindowTags.missionRow("other-broken"))
            waitForIdle()
            onNodeWithTag(WindowTags.projectRow("project0001")).assertIsNotSelected()
            onNodeWithTag(WindowTags.projectRow("project0002")).assertIsNotSelected()
        }

    @Test
    fun `FR-S9 clicking a project leaves the global view`() =
        runComposeUiTest {
            // Given the project "Agent Manager" selected and then the working view shown
            show()
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.view("working")).performClick()
            await(WindowTags.missionRow("other-work"))
            waitForIdle()

            // When the user clicks the same project again
            await(WindowTags.projectRow("project0001")).performClick()

            // Then the page of that project is back with its own sessions only
            await(WindowTags.missionRow("mine-ask"))
            gone(WindowTags.missionRow("other-work"))
            gone(WindowTags.missionRow("none-work"))
            await(WindowTags.projectRow("project0001")).assertIsSelected()
        }

    @Test
    fun `FR-K1 palette opens the failed view`() =
        runComposeUiTest {
            // Given a selected project
            val palette = show()
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("mine-work"))

            // When the user opens the palette, searches "failed" and picks "Widok: Błędy"
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("failed")
            await(WindowTags.paletteRow("sessions.view.failed")).performClick()

            // Then the failed sessions of every project are listed and the palette closes
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            await(WindowTags.missionRow("other-broken"))
            gone(WindowTags.missionRow("mine-work"))
            onAllNodes(hasText("Błędy", substring = true) and hasAnyAncestor(hasTestTag(WindowTags.HEADER)))
                .onFirst()
                .assertExists()
        }

    @Test
    fun `FR-K1 palette offers no filter commands in a global view`() =
        runComposeUiTest {
            // Given the failed view shown
            val palette = show()
            await(WindowTags.view("failed")).performClick()
            await(WindowTags.missionRow("other-broken"))
            waitForIdle()

            // When the user opens the palette
            runOnIdle { palette.open() }

            // Then it lists the other session commands but no filter rows
            await(WindowTags.paletteRow("sessions.next"))
            await(WindowTags.paletteRow("sessions.view.working"))
            onNodeWithTag(WindowTags.paletteRow("sessions.filter.active")).assertDoesNotExist()
            onNodeWithTag(WindowTags.paletteRow("sessions.filter.all")).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 j and x work in the working view`() =
        runComposeUiTest {
            // Given the working view shown (the newest working session, none-work, is the first row)
            val fake = world()
            show(fake)
            await(WindowTags.view("working")).performClick()
            await(WindowTags.missionRow("none-work"))
            waitForIdle()

            // When the user presses j, then x
            press(Key.J)
            await(WindowTags.DETAILS_STOP)
            press(Key.X)

            // Then the session of the first row, which has no project, is stopped and leaves the view
            gone(WindowTags.missionRow("none-work"))
            fake.calls shouldBe listOf("stop none-work")
            await(WindowTags.missionRow("other-work"))
        }
}
