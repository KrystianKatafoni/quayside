package dev.quayside.app

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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Reply (`r`) to the selected waiting session from the keyboard and the palette, in the whole window with the terminal
 * panel on fakes (FR-K2, FR-K1; AM-506). Session ids equal their names; `claude attach <id>` "runs" in a temp dir.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private lateinit var fake: FakeSessions
    private val asking = SessionState.AwaitingInput("Continue?")

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun attached(vararg names: String): List<ShellCommand> = names.map { attachCommand(SessionId(it)) }

    private fun session(
        name: String,
        startedAtSecond: Long,
        state: SessionState = asking,
    ): WorldSession =
        WorldSession(
            id = SessionId(name),
            cwd = A_PROJECT_DIR,
            state = state,
            name = name,
            startedAt = T0.plusSeconds(startedAtSecond),
        )

    private fun ComposeUiTest.window(vararg list: WorldSession): CommandPaletteController {
        val world = SessionsWorld(listOf(WorldProject(agent.id, agent.dir)), list.toList())
        fake = FakeSessions(world)
        return showWindow(FakeProjectsApi(initial = listOf(agent)), fake, terminals, ::attachCommand)
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

    private fun ComposeUiTest.assertNoTab() {
        waitForIdle()
        onNodeWithTag(WindowTags.terminalTab(1)).assertDoesNotExist()
        terminals.fake.commands.shouldBeEmpty()
    }

    @Test
    fun `FR-K2 r opens an attach tab of the selected waiting session`() =
        runComposeUiTest {
            // Given a selected session that waits for the user
            window(session("fix-login", 0))
            selectSession("fix-login")

            // When the user presses r
            press(Key.R)

            // Then `claude attach` of that session runs in a new tab of the project group
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Quayside", substring = true)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 r on a working session opens nothing`() =
        runComposeUiTest {
            // Given a selected session that works
            window(session("fix-login", 0, SessionState.Working))
            selectSession("fix-login")

            // When the user presses r
            press(Key.R)

            // Then no tab opens
            assertNoTab()
        }

    @Test
    fun `FR-K2 r without a selected session opens nothing`() =
        runComposeUiTest {
            // Given a selected project with a waiting session, none selected
            window(session("fix-login", 0))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("fix-login"))

            // When the user presses r
            press(Key.R)

            // Then no tab opens
            assertNoTab()
        }

    @Test
    fun `FR-K2 j then r replies to the newly selected waiting session`() =
        runComposeUiTest {
            // Given a working session selected above a waiting one
            window(session("older-work", 0), session("newer-work", 60, SessionState.Working))
            selectSession("newer-work")

            // When the user presses j, then r after the UI settles
            press(Key.J)
            press(Key.R)

            // Then the waiting session below is attached
            await(WindowTags.terminalTab(1)).assertTextContains("attach · older-work", substring = true)
            terminals.fake.commands shouldBe attached("older-work")
        }

    @Test
    fun `FR-K2 r with the preview open replies and keeps the preview`() =
        runComposeUiTest {
            // Given a selected waiting session with an open preview
            window(session("fix-login", 0))
            selectSession("fix-login")
            await(WindowTags.DETAILS_PREVIEW).performClick()
            await(WindowTags.PREVIEW)

            // When the user presses r
            press(Key.R)

            // Then the session is attached and the preview stays
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.PREVIEW)
            terminals.fake.commands shouldBe attached("fix-login")
        }

    @Test
    fun `FR-K2 r does nothing while the remove confirmation is open`() =
        runComposeUiTest {
            // Given the confirmation of removing a finished session
            // (in the window the focusable dialog Popup takes the keys; the dialog guards themselves are covered by
            // AttachCommandsTest "no reply while a mission control dialog is open" and
            // SessionAttachControllerTest "actions offer no reply with a dialog open")
            window(session("fix-login", 0, SessionState.Done))
            selectSession("fix-login")
            await(WindowTags.DETAILS_REMOVE).performClick()
            await(WindowTags.CONFIRM_REMOVE)

            // When the user presses r
            press(Key.R)

            // Then nothing is attached and the dialog stays
            assertNoTab()
            await(WindowTags.CONFIRM_REMOVE)
        }

    @Test
    fun `FR-K2 r typed in the palette search does not reply`() =
        runComposeUiTest {
            // Given a selected waiting session and the open palette
            val palette = window(session("fix-login", 0))
            selectSession("fix-login")
            runOnIdle { palette.open() }

            // When the user types "r" into the search field
            await(WindowTags.PALETTE_FIELD).performTextInput("r")
            waitForIdle()

            // Then the letter goes to the field and nothing is attached
            await(WindowTags.PALETTE_FIELD)
            assertNoTab()
        }

    @Test
    fun `FR-K1 palette reply opens the tab in the session project group`() =
        runComposeUiTest {
            // Given a selected waiting session
            val palette = window(session("fix-login", 0))
            selectSession("fix-login")

            // When the user runs "Odpowiedz w terminalu" from the palette
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Odpowiedz")
            await(WindowTags.paletteRow("sessions.reply")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))

            // Then the tab shows in the group of the session's project
            await(WindowTags.terminalTab(1)).assertTextContains("attach · fix-login", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Quayside", substring = true)
            terminals.fake.commands shouldBe attached("fix-login")
        }
}
