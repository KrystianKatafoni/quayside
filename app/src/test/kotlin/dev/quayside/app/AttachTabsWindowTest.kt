package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.ResourceBundle

private const val WAIT_MS = 5_000L

/**
 * One tab per attached session and the state dot on it, in the whole window on fakes (FR-K2, FR-K1, FR-T2; AM-510).
 * Session ids equal their names; the fake terminal numbers its sessions `fake-1`, `fake-2`, … in opening order.
 */
@OptIn(ExperimentalTestApi::class)
class AttachTabsWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agentDir by lazy { Files.createDirectory(root.resolve("agent")) }
    private val agent by lazy { aProject(id = "project0001", dir = agentDir, displayName = "Quayside") }
    private lateinit var fakeSessions: FakeSessions

    /** The dot text from the terminal bundle (`TerminalStrings` is internal to the terminal module). */
    private fun statusText(name: String): String =
        ResourceBundle
            .getBundle(
                "i18n.terminal",
                Locale.forLanguageTag("pl"),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
            ).getString("tab.status.$name")

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun attached(vararg names: String): List<ShellCommand> = names.map { attachCommand(SessionId(it)) }

    private fun session(
        name: String,
        startedAtSecond: Long,
        state: SessionState = SessionState.Working,
    ): WorldSession = WorldSession(SessionId(name), agentDir, state, name, T0.plusSeconds(startedAtSecond))

    private fun ComposeUiTest.window(vararg list: WorldSession): CommandPaletteController {
        val sessions = FakeSessions(SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), list.toList()))
        fakeSessions = sessions
        return showWindow(FakeProjectsApi(initial = listOf(agent)), sessions, terminals, ::attachCommand)
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.selectSession(name: String) {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow(name)).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
    }

    private fun ComposeUiTest.openShellTab() {
        await(WindowTags.terminalGroupAdd("project0001")).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.stateText(tab: Int): String? =
        onAllNodes(hasTestTag(WindowTags.terminalTabStatus(tab)))
            .fetchSemanticsNodes()
            .singleOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun `FR-K2 Enter twice keeps one attach tab`() =
        runComposeUiTest {
            // Given a selected session
            window(session("fix-login", 0))
            selectSession("fix-login")

            // When the user presses Enter twice
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            press(Key.Enter)

            // Then one tab runs `claude attach` of the session
            onNodeWithTag(WindowTags.terminalTab(2)).assertDoesNotExist()
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 Enter on the first session again selects its tab`() =
        runComposeUiTest {
            // Given the sessions newer-work (first row) and older-work, each with its own attach tab
            window(session("older-work", 0), session("newer-work", 60))
            selectSession("newer-work")
            press(Key.Enter)
            press(Key.J)
            press(Key.Enter)
            await(WindowTags.terminalTab(2)).assertIsSelected()

            // When the user goes back to the first session and presses Enter
            press(Key.K)
            press(Key.Enter)

            // Then its tab is shown again and no new process starts
            await(WindowTags.terminalTab(1)).assertIsSelected()
            await(WindowTags.terminalTab(2)).assertIsNotSelected()
            onNodeWithTag(WindowTags.terminalTab(3)).assertDoesNotExist()
            terminals.fake.commands shouldBe attached("newer-work", "older-work")
        }

    @Test
    fun `FR-K2 r on a session with an attach tab selects that tab`() =
        runComposeUiTest {
            // Given a waiting session with its attach tab, hidden behind a shell tab
            window(session("fix-login", 0, SessionState.AwaitingInput("Continue?")))
            selectSession("fix-login")
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            openShellTab()
            await(WindowTags.terminalTab(2)).assertIsSelected()

            // When the user presses r
            press(Key.R)

            // Then the attach tab is shown and no second process starts
            await(WindowTags.terminalTab(1)).assertIsSelected()
            onNodeWithTag(WindowTags.terminalTab(3)).assertDoesNotExist()
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 Enter after the attach exited replaces the tab`() =
        runComposeUiTest {
            // Given an attach tab whose process ended (detach)
            window(session("fix-login", 0))
            selectSession("fix-login")
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            runOnIdle {
                terminals.fake.sessions
                    .single()
                    .finish(0)
            }
            await(WindowTags.terminalTabExit(1))

            // When the user presses Enter
            press(Key.Enter)

            // Then a new process takes the place of the dead tab
            await(WindowTags.terminalTab(2)).assertTextContains("attach · fix-login", substring = true)
            waitUntilDoesNotExist(hasTestTag(WindowTags.terminalTab(1)))
            terminals.fake.commands shouldBe attached("fix-login", "fix-login")
            terminals.fake.sessions
                .first()
                .isClosed shouldBe true
        }

    @Test
    fun `FR-K1 palette attach selects the existing tab`() =
        runComposeUiTest {
            // Given an attach tab hidden behind a shell tab
            val palette = window(session("fix-login", 0))
            selectSession("fix-login")
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            openShellTab()
            await(WindowTags.terminalTab(2)).assertIsSelected()

            // When the user runs "Attach sesji w terminalu" from the palette
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Attach sesji")
            await(WindowTags.paletteRow("sessions.attach")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))

            // Then the existing tab is shown and nothing new starts
            await(WindowTags.terminalTab(1)).assertIsSelected()
            onNodeWithTag(WindowTags.terminalTab(3)).assertDoesNotExist()
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-T2 attach tab shows the state dot of its session`() =
        runComposeUiTest {
            // Given a working session
            window(session("fix-login", 0))
            selectSession("fix-login")

            // When its attach tab opens
            press(Key.Enter)

            // Then the tab shows a dot with the text of the working state
            await(WindowTags.terminalTab(1))
            waitUntil(timeoutMillis = WAIT_MS) { stateText(1) != null }
            stateText(1) shouldBe statusText("working")
        }

    @Test
    fun `FR-T2 dot follows the session state`() =
        runComposeUiTest {
            // Given an attach tab of a working session
            window(session("fix-login", 0))
            selectSession("fix-login")
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            waitUntil(timeoutMillis = WAIT_MS) { stateText(1) != null }
            stateText(1) shouldBe statusText("working")

            // When the session waits for permission
            runTest { fakeSessions.setState(SessionId("fix-login"), SessionState.AwaitingPermission("Bash")) }

            // Then the dot shows another text
            waitUntil(timeoutMillis = WAIT_MS) { stateText(1) != statusText("working") }
            stateText(1) shouldBe statusText("waiting")
        }

    @Test
    fun `FR-T2 shell tab has no dot`() =
        runComposeUiTest {
            // Given an attach tab with its dot and a shell tab of the same project
            window(session("fix-login", 0))
            selectSession("fix-login")
            press(Key.Enter)
            await(WindowTags.terminalTab(1))
            waitUntil(timeoutMillis = WAIT_MS) { stateText(1) != null }
            openShellTab()
            await(WindowTags.terminalTab(2)).assertIsSelected()

            // Then only the attach tab shows a dot
            waitForIdle()
            onNodeWithTag(WindowTags.terminalTabStatus(2)).assertDoesNotExist()
            stateText(1).orEmpty().shouldNotBeBlank()
        }
}
