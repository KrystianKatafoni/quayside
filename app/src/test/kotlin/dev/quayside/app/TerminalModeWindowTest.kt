package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
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
import java.nio.file.Files
import java.nio.file.Path

/**
 * The terminal mode of the window (plan AM-809, mockup B) on fakes: a narrow bar of project icons, the session bar and
 * the terminal instead of the list. Attach never changes the mode (docs/06: the full screen is a button or a palette
 * command). Session ids equal their names; `claude attach <id>` "runs" in a temp dir.
 */
@OptIn(ExperimentalTestApi::class)
class TerminalModeWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent by lazy { aProject(id = "project0001", dir = root.resolve("agent").also(Files::createDirectory)) }
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")
    private val sessions by lazy {
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(agent.id, agent.dir), WorldProject(other.id, other.dir)),
                listOf(
                    WorldSession(
                        SessionId("fix-login"),
                        agent.dir,
                        SessionState.AwaitingInput(null),
                        "fix-login",
                        T0,
                    ),
                    WorldSession(SessionId("popup1"), agent.dir, SessionState.Working, "popup1", T0.plusSeconds(1)),
                ),
            ),
        )
    }

    private lateinit var setup: WindowSetup

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun ComposeUiTest.window(): CommandPaletteController {
        setup = WindowSetup(FakeProjectsApi(initial = listOf(agent, other)), sessions, terminals, ::attachCommand)
        return showWindowOf(setup)
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    /** Selects the project and the session "fix-login" and attaches it with Enter: the list mode with one tab. */
    private fun ComposeUiTest.attached() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow("fix-login")).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
        press(Key.Enter)
        await(WindowTags.terminalTab(1))
    }

    private fun ComposeUiTest.openPalette(
        palette: CommandPaletteController,
        query: String,
    ) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(query)
    }

    private fun ComposeUiTest.fullScreen() {
        await(WindowTags.FULL_SCREEN).performClick()
        await(WindowTags.RAIL)
    }

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.assertListMode() {
        await(WindowTags.SIDEBAR)
        await(WindowTags.MISSION_CONTROL)
        has(WindowTags.RAIL) shouldBe false
        has(WindowTags.SESSION_BAR) shouldBe false
    }

    @Test
    fun `full screen shows rail, session bar and terminal without mission control`() =
        runComposeUiTest {
            // Given an attached session in the list mode
            window()
            attached()

            // When the user presses the full screen button of the tab bar
            fullScreen()

            // Then the rail, the session bar of the attached session and the terminal fill the window, and neither
            // the sidebar nor Mission Control is drawn
            await(WindowTags.SESSION_BAR).assertTextContains("fix-login", substring = true)
            await(WindowTags.TERMINAL_AREA)
            await(ModeTags.BACK)
            has(WindowTags.SIDEBAR) shouldBe false
            has(WindowTags.MISSION_CONTROL) shouldBe false
            terminals.fake.commands.size shouldBe 1
        }

    @Test
    fun `back returns to the list with the sidebar`() =
        runComposeUiTest {
            window()
            attached()
            fullScreen()

            await(ModeTags.BACK).performClick()

            waitUntilDoesNotExist(hasTestTag(WindowTags.RAIL))
            assertListMode()
            await(WindowTags.terminalTab(1))
        }

    @Test
    fun `closing the last tab leaves terminal mode`() =
        runComposeUiTest {
            window()
            attached()
            fullScreen()

            await(WindowTags.terminalTabClose(1)).performClick()

            waitUntilDoesNotExist(hasTestTag(WindowTags.RAIL))
            assertListMode()
        }

    @Test
    fun `closing the last shell tab leaves terminal mode`() =
        runComposeUiTest {
            // Given an attach tab and a shell tab (no key) in the terminal mode
            window()
            attached()
            await(WindowTags.terminalGroupAdd("project0001")).performClick()
            await(WindowTags.terminalTab(2))
            fullScreen()

            // When the user closes the attach tab, the shell tab keeps the mode
            await(WindowTags.terminalTabClose(1)).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.terminalTab(1)))
            await(WindowTags.RAIL)

            // And closing the shell tab (its active key stays null) leaves it
            await(WindowTags.terminalTabClose(2)).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.RAIL))
            assertListMode()
        }

    @Test
    fun `popup attach selects the project in the sidebar`() =
        runComposeUiTest {
            window()
            await(WindowTags.projectRow("project0001")).assertIsNotSelected()

            runOnIdle {
                checkNotNull(setup.attacher)
                    .attachSession(SessionId("popup1"), sessions.snapshot.value.sessions, listOf(agent, other))
            }

            await(WindowTags.terminalTab(1))
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(WindowTags.projectRow("project0002")).assertIsNotSelected()
        }

    @Test
    fun `rail project click selects it and returns to the list`() =
        runComposeUiTest {
            window()
            attached()
            fullScreen()

            await(WindowTags.railButton("project0002")).performClick()

            waitUntilDoesNotExist(hasTestTag(WindowTags.RAIL))
            assertListMode()
            await(WindowTags.projectRow("project0002")).assertIsSelected()
        }

    @Test
    fun `palette commands enter and leave terminal mode`() =
        runComposeUiTest {
            // Given a tab and the open palette
            val palette = window()
            attached()
            openPalette(palette, "tryb")

            // When the user picks "Tryb terminala"
            await(WindowTags.paletteRow(TERMINAL_MODE_COMMAND)).performClick()

            // Then the terminal mode is shown; its palette offers the way back
            await(WindowTags.RAIL)
            openPalette(palette, "wróć")
            await(WindowTags.paletteRow(BACK_TO_LIST_COMMAND)).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.RAIL))
            assertListMode()
        }

    @Test
    fun `attach keeps the list mode`() =
        runComposeUiTest {
            window()

            attached()

            assertListMode()
            await(WindowTags.TERMINAL_AREA)
        }

    @Test
    fun `popup attach selects the session so the bar shows it`() =
        runComposeUiTest {
            // Given the list mode with no session selected
            val palette = window()
            await(WindowTags.projectRow("project0001"))

            // When a popup Attach opens the tab of "popup1" and the user enters the terminal mode
            runOnIdle {
                checkNotNull(setup.attacher).attachSession(
                    SessionId("popup1"),
                    sessions.snapshot.value.sessions,
                    listOf(agent, other),
                )
            }
            await(WindowTags.terminalTab(1))
            openPalette(palette, "tryb")
            await(WindowTags.paletteRow(TERMINAL_MODE_COMMAND)).performClick()

            // Then the session bar shows that session
            await(WindowTags.SESSION_BAR).assertTextContains("popup1", substring = true)
        }

    @Test
    fun `session bar reply opens the reply tab`() =
        runComposeUiTest {
            // Given an attached, waiting session whose terminal process has ended
            window()
            attached()
            runOnIdle {
                terminals.fake.sessions
                    .single()
                    .finish(0)
            }
            await(WindowTags.terminalTabExit(1))
            fullScreen()

            // When the user clicks Odpowiedz in the session bar
            await(WindowTags.SESSION_BAR_REPLY).performClick()

            // Then the exited tab is replaced by a new `claude attach` of the same session
            waitUntil { terminals.fake.commands.size == 2 }
            terminals.fake.commands shouldBe
                listOf(attachCommand(SessionId("fix-login")), attachCommand(SessionId("fix-login")))
        }
}
