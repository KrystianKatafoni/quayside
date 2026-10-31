package dev.agentmanager.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.T0
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Attach of the selected session from `⏎` and from the palette, in the whole window with the terminal panel on fakes
 * (FR-K2, FR-K1; AM-505). Session ids equal their names; `claude attach <id>` "runs" in a temp dir. `WindowKeys`
 * stands on a focused `Box` (see `showWindow`).
 */
@OptIn(ExperimentalTestApi::class)
class AttachWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun attached(vararg names: String): List<ShellCommand> = names.map { attachCommand(SessionId(it)) }

    private fun session(
        name: String,
        startedAtSecond: Long,
        state: SessionState = SessionState.Working,
    ): WorldSession =
        WorldSession(
            id = SessionId(name),
            cwd = A_PROJECT_DIR,
            state = state,
            name = name,
            startedAt = T0.plusSeconds(startedAtSecond),
        )

    private fun sessions(vararg list: WorldSession): FakeSessions =
        FakeSessions(SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), list.toList()))

    private fun ComposeUiTest.window(vararg list: WorldSession): CommandPaletteController =
        showWindow(FakeProjectsApi(initial = listOf(agent)), sessions(*list), terminals, ::attachCommand)

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.selectProject() {
        await(WindowTags.projectRow("project0001")).performClick()
    }

    private fun ComposeUiTest.selectSession(name: String) {
        await(WindowTags.missionRow(name)).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
    }

    private fun ComposeUiTest.assertNoTab() {
        waitForIdle()
        onNodeWithTag(WindowTags.terminalTab(1)).assertDoesNotExist()
    }

    @Test
    fun `FR-K2 Enter opens an attach tab of the selected session`() =
        runComposeUiTest {
            // Given a selected project with a selected session
            window(session("fix-login", 0))
            selectProject()
            selectSession("fix-login")

            // When the user presses Enter
            press(Key.Enter)

            // Then `claude attach` of that session runs in a new tab of the project group
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Agent Manager", substring = true)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 j then Enter attaches the newly selected session`() =
        runComposeUiTest {
            // Given two sessions, the newer one (first row) selected
            window(session("older-work", 0), session("newer-work", 60))
            selectProject()
            selectSession("newer-work")

            // When the user presses j, then Enter after the UI settles
            press(Key.J)
            press(Key.Enter)

            // Then the session below is attached
            await(WindowTags.terminalTab(1)).assertTextContains("attach · older-work", substring = true)
            terminals.fake.commands shouldBe attached("older-work")
        }

    @Test
    fun `FR-K2 Enter without a selected session opens nothing`() =
        runComposeUiTest {
            // Given a selected project with a session, none selected
            window(session("fix-login", 0))
            selectProject()
            await(WindowTags.missionRow("fix-login"))

            // When the user presses Enter
            press(Key.Enter)

            // Then no tab opens
            assertNoTab()
            terminals.fake.commands.shouldBeEmpty()
        }

    @Test
    fun `FR-K1 palette attach opens the tab in the session project group`() =
        runComposeUiTest {
            // Given a selected session
            val palette = window(session("fix-login", 0))
            selectProject()
            selectSession("fix-login")

            // When the user runs "Attach sesji w terminalu" from the palette
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Attach sesji")
            await(WindowTags.paletteRow("sessions.attach")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))

            // Then the tab shows in the group of the session's project
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Agent Manager", substring = true)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 Enter after clicking Podgląd attaches and keeps the preview`() =
        runComposeUiTest {
            // Given a selected session with an open preview (the button was clicked)
            window(session("fix-login", 0))
            selectProject()
            selectSession("fix-login")
            await(WindowTags.DETAILS_PREVIEW).performClick()
            await(WindowTags.PREVIEW)

            // When the user presses Enter
            press(Key.Enter)

            // Then the session is attached (the button did not click again) and the preview stays
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.PREVIEW)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 Enter confirming a palette row does not attach`() =
        runComposeUiTest {
            // Given a selected session and the palette narrowed to a harmless command
            val palette = window(session("fix-login", 0))
            selectProject()
            selectSession("fix-login")
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Filtr: Aktywne")
            await(WindowTags.paletteRow("sessions.filter.active"))

            // When the user presses Enter in the palette field
            onNodeWithTag(WindowTags.PALETTE_FIELD).performKeyInput {
                keyDown(Key.Enter)
                keyUp(Key.Enter)
            }

            // Then the palette runs its row and closes; nothing is attached
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            assertNoTab()
            terminals.fake.commands.shouldBeEmpty()
        }

    @Test
    fun `FR-K2 Enter does nothing while the remove confirmation is open`() =
        runComposeUiTest {
            // Given the confirmation of removing a finished session
            window(session("fix-login", 0, SessionState.Done))
            selectProject()
            selectSession("fix-login")
            await(WindowTags.DETAILS_REMOVE).performClick()
            await(WindowTags.CONFIRM_REMOVE)

            // When the user presses Enter
            press(Key.Enter)

            // Then nothing is attached and the dialog stays
            assertNoTab()
            await(WindowTags.CONFIRM_REMOVE)
            terminals.fake.commands.shouldBeEmpty()
        }
}
