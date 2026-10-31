package dev.agentmanager.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
import org.junit.jupiter.api.Test

/**
 * Window keys j, k and g p in the whole window on fakes (FR-K2, AM-409). `WindowKeys` stands on a focused `Box`
 * (see `showWindow`); that `Window.onKeyEvent` also gets keys without focus is a manual check.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    private fun session(
        name: String,
        startedAtSecond: Long,
    ): WorldSession =
        WorldSession(
            id = SessionId(name),
            cwd = A_PROJECT_DIR,
            state = SessionState.Working,
            name = name,
            startedAt = T0.plusSeconds(startedAtSecond),
        )

    private fun sessions(vararg list: WorldSession): FakeSessions =
        FakeSessions(SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), list.toList()))

    /** Rows are newest first, so `newer-work` is the first row. */
    private fun twoSessions(): FakeSessions = sessions(session("older-work", 0), session("newer-work", 60))

    private fun ComposeUiTest.press(keys: List<Key>) =
        keys.forEach { key ->
            onNodeWithTag(WindowTags.KEYS).performKeyInput {
                keyDown(key)
                keyUp(key)
            }
            waitForIdle()
        }

    private fun ComposeUiTest.detailsShow(name: String) =
        waitUntil {
            onAllNodes(hasText(name, substring = true) and hasAnyAncestor(hasTestTag(WindowTags.DETAILS)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

    @Test
    fun `FR-K2 j and k move the selection between sessions`() =
        runComposeUiTest {
            // Given a selected project with two sessions and no session selected
            showWindow(FakeProjectsApi(initial = listOf(agent)), twoSessions())
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the user presses j
            press(listOf(Key.J))

            // Then the first row (newer-work) is open in the details panel
            detailsShow("newer-work")

            // When the user presses j again, then k
            press(listOf(Key.J))
            detailsShow("older-work")
            press(listOf(Key.K))

            // Then the selection moves back
            detailsShow("newer-work")
        }

    @Test
    fun `FR-K2 g p opens the palette with projects only`() =
        runComposeUiTest {
            // Given two projects and the closed palette
            showWindow(FakeProjectsApi(initial = listOf(agent, other)))
            await(WindowTags.projectRow("project0002"))

            // When the user presses g and then p
            press(listOf(Key.G, Key.P))

            // Then the palette lists only the go-to-project commands
            await(WindowTags.paletteRow("projects.goto.project0001"))
            await(WindowTags.paletteRow("projects.goto.project0002"))
            onNodeWithTag(WindowTags.paletteRow("projects.add")).assertDoesNotExist()
            onNodeWithTag(WindowTags.paletteRow("projects.switch")).assertDoesNotExist()
            onNodeWithTag(WindowTags.paletteRow("sessions.next")).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 letters do nothing while a dialog is open`() =
        runComposeUiTest {
            // Given a selected project with sessions and the rename dialog open
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent, other)), twoSessions())
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("rename")
            await(WindowTags.paletteRow("projects.rename")).performClick()
            await(WindowTags.RENAME_DIALOG)

            // When the user presses j and then g p
            press(listOf(Key.J, Key.G, Key.P))

            // Then no session is selected and no palette opened
            onNodeWithTag(WindowTags.DETAILS_EMPTY).assertExists()
            onNodeWithTag(WindowTags.DETAILS).assertDoesNotExist()
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }
}
