package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Attach and reply with the mouse in the whole window with the terminal panel on fakes (FR-K3, AM-511): the buttons,
 * the row menu and a double click take the same path as `⏎` and `r`, so they open the same `attach` tab.
 */
@OptIn(ExperimentalTestApi::class)
class AttachMouseWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun ComposeUiTest.window(state: SessionState) {
        val session = WorldSession(SessionId("fix-login"), A_PROJECT_DIR, state, "fix-login", T0)
        val sessions = FakeSessions(SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), listOf(session)))
        showWindow(FakeProjectsApi(initial = listOf(agent)), sessions, terminals, ::attachCommand)
        await(WindowTags.projectRow("project0001")).performClick()
    }

    private fun attached() = listOf(attachCommand(SessionId("fix-login")))

    @Test
    fun `FR-K3 the Attach button opens the attach tab of the selected session`() =
        runComposeUiTest {
            // Given a selected working session
            window(SessionState.Working)
            await(WindowTags.missionRow("fix-login")).performClick()

            // When the user clicks Attach in the panel
            await(WindowTags.DETAILS_ATTACH).performClick()

            // Then `claude attach` of that session runs in a new tab of the project group
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            terminals.fake.commands shouldBe attached()
        }

    @Test
    fun `FR-K3 a double click on a row attaches without a prior selection`() =
        runComposeUiTest {
            // Given a project with a session, nothing selected
            window(SessionState.Working)

            // When the user double-clicks the row
            await(WindowTags.missionRow("fix-login")).performMouseInput { doubleClick() }

            // Then the attach tab is open
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            terminals.fake.commands shouldBe attached()
        }

    @Test
    fun `FR-K3 Odpowiedz and the row menu of a waiting session open the one attach tab`() =
        runComposeUiTest {
            // Given a session that waits for an answer
            window(SessionState.AwaitingInput(null))

            // When the user picks Attach in the menu of the row, then clicks Odpowiedz in the panel
            await(WindowTags.missionRowMenuButton("fix-login")).performClick()
            await(WindowTags.ROW_MENU_ATTACH).performClick()
            await(WindowTags.terminalTab(1))
            await(WindowTags.DETAILS_REPLY).performClick()
            waitForIdle()

            // Then both use the same tab and start one process
            terminals.fake.commands shouldBe attached()
        }
}
