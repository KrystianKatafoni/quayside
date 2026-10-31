package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Window shortcuts after a click on a sidebar button (issue #108, FR-K2, AM-505). The window picker answers null, so
 * "Dodaj projekt" opens no dialog (and no focusable `Popup`): the test checks the focus of the button itself and that
 * `⏎` still reaches `WindowKeys` and attaches. With the focused button, Enter would click it instead.
 */
@OptIn(ExperimentalTestApi::class)
class SidebarFocusWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    @Test
    fun `FR-K2 Enter attaches the selected session after clicking add project`() =
        runComposeUiTest {
            // Given a selected project with a selected session
            val session = WorldSession(SessionId("fix-login"), A_PROJECT_DIR, name = "fix-login", startedAt = T0)
            val world = SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), listOf(session))
            showWindow(FakeProjectsApi(initial = listOf(agent)), FakeSessions(world), terminals, ::attachCommand)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("fix-login")).performClick()
            await(WindowTags.DETAILS_PREVIEW)

            // When the user clicks "Dodaj projekt" (the chooser is cancelled) and presses Enter
            await(WindowTags.ADD_BUTTON).performClick()
            waitForIdle()
            onNodeWithTag(WindowTags.ADD_BUTTON).assertIsNotFocused()
            press(Key.Enter)

            // Then the session is attached
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            terminals.fake.commands shouldBe listOf(attachCommand(SessionId("fix-login")))
        }
}
