package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.commandpalette.ui.CommandPaletteController
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Stop and Respawn from the window key `x` and from the palette, in the whole window on fakes (FR-K2, FR-K1,
 * FR-S4; AM-410). Session ids equal their names. `WindowKeys` stands on a focused `Box` (see `showWindow`).
 */
@OptIn(ExperimentalTestApi::class)
class SessionActionsWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

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

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    /** Clicks the project, then the session; waits until its panel offers [button] and the commands settled. */
    private fun ComposeUiTest.selectSession(
        name: String,
        button: String,
    ) {
        await(WindowTags.missionRow(name)).performClick()
        await(button)
        waitForIdle()
    }

    @Test
    fun `FR-K2 x stops the selected session`() =
        runComposeUiTest {
            // Given a selected project with a working session, selected
            val fake = sessions(session("fix-login", 0))
            showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("fix-login", WindowTags.DETAILS_STOP)

            // When the user presses x
            press(Key.X)

            // Then the session is stopped and the panel offers Respawn instead
            fake.calls shouldBe listOf("stop fix-login")
            await(WindowTags.DETAILS_RESPAWN)
            onNodeWithTag(WindowTags.DETAILS_STOP).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 j then x stops the next session`() =
        runComposeUiTest {
            // Given a selected project with two working sessions and none selected (newer-work is the first row)
            val fake = sessions(session("older-work", 0), session("newer-work", 60))
            showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the user presses j and then x
            press(Key.J)
            await(WindowTags.DETAILS_STOP)
            press(Key.X)

            // Then the session that j selected is stopped, not the other one
            fake.calls shouldBe listOf("stop newer-work")
        }

    @Test
    fun `FR-K2 x does nothing while a dialog is open`() =
        runComposeUiTest {
            // Given a selected working session and the rename dialog open
            val fake = sessions(session("fix-login", 0))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("fix-login", WindowTags.DETAILS_STOP)
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("rename")
            await(WindowTags.paletteRow("projects.rename")).performClick()
            await(WindowTags.RENAME_DIALOG)

            // When the user presses x
            press(Key.X)

            // Then nothing is stopped
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-K1 FR-S4 palette offers respawn for a stopped session only`() =
        runComposeUiTest {
            // Given a stopped and a working session
            val fake = sessions(session("old-one", 0, SessionState.Stopped), session("live-one", 60))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()

            // When the stopped session is selected and the palette opens
            selectSession("old-one", WindowTags.DETAILS_RESPAWN)
            runOnIdle { palette.open() }

            // Then Respawn is offered and Stop is not
            await(WindowTags.paletteRow("sessions.respawn"))
            onNodeWithTag(WindowTags.paletteRow("sessions.stop")).assertDoesNotExist()

            // When the working session is selected and the palette opens again
            runOnIdle { palette.close() }
            selectSession("live-one", WindowTags.DETAILS_STOP)
            runOnIdle { palette.open() }

            // Then Stop is offered and Respawn is not
            await(WindowTags.paletteRow("sessions.stop"))
            onNodeWithTag(WindowTags.paletteRow("sessions.respawn")).assertDoesNotExist()

            // When the user picks "Zatrzymaj sesję"
            onNodeWithTag(WindowTags.paletteRow("sessions.stop")).performClick()

            // Then the working session is stopped
            waitUntil { fake.calls == listOf("stop live-one") }
        }

    private fun ComposeUiTest.pickFromPalette(
        palette: CommandPaletteController,
        commandId: String,
    ) {
        runOnIdle { palette.open() }
        await(WindowTags.paletteRow(commandId)).performClick()
    }

    @Test
    fun `FR-K1 FR-S4 palette remove asks for confirmation`() =
        runComposeUiTest {
            // Given a selected project with a stopped session, selected
            val fake = sessions(session("old-one", 0, SessionState.Stopped))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("old-one", WindowTags.DETAILS_REMOVE)

            // When the user picks "Usuń sesję z listy" in the palette
            pickFromPalette(palette, "sessions.remove")

            // Then a confirmation opens and nothing is removed yet
            await(WindowTags.CONFIRM_REMOVE)
            fake.calls.shouldBeEmpty()

            // When the user confirms
            await(WindowTags.CONFIRM_REMOVE_OK).performClick()

            // Then the session is removed and the panel asks to pick a session
            waitUntil { fake.calls == listOf("remove old-one") }
            await(WindowTags.DETAILS_EMPTY)
            onNodeWithTag(WindowTags.CONFIRM_REMOVE).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 j and x do nothing while the confirmation is open`() =
        runComposeUiTest {
            // Given a stopped session, selected, below a working one, and the confirmation open
            val fake = sessions(session("old-one", 0, SessionState.Stopped), session("live-one", 60))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("old-one", WindowTags.DETAILS_REMOVE)
            pickFromPalette(palette, "sessions.remove")
            await(WindowTags.CONFIRM_REMOVE)

            // When the user presses k, j and x
            press(Key.K)
            press(Key.J)
            press(Key.X)

            // Then nothing is stopped or removed and the selection stays
            fake.calls.shouldBeEmpty()
            await(WindowTags.CONFIRM_REMOVE_CANCEL).performClick()
            await(WindowTags.DETAILS_REMOVE)
            onNodeWithTag(WindowTags.DETAILS_STOP).assertDoesNotExist()
        }

    @Test
    fun `FR-K1 palette offers remove for a finished session only`() =
        runComposeUiTest {
            // Given a stopped and a working session
            val fake = sessions(session("old-one", 0, SessionState.Stopped), session("live-one", 60))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()

            // When the stopped session is selected and the palette opens
            selectSession("old-one", WindowTags.DETAILS_REMOVE)
            runOnIdle { palette.open() }

            // Then "Usuń sesję z listy" is offered
            await(WindowTags.paletteRow("sessions.remove"))

            // When the working session is selected and the palette opens again
            runOnIdle { palette.close() }
            selectSession("live-one", WindowTags.DETAILS_STOP)
            runOnIdle { palette.open() }

            // Then it is not offered
            await(WindowTags.paletteRow("sessions.stop"))
            onNodeWithTag(WindowTags.paletteRow("sessions.remove")).assertDoesNotExist()
        }
}
