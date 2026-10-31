package dev.agentmanager.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The output preview from the window key `space` and from the palette, in the whole window on fakes (FR-K2, FR-K1;
 * AM-411). Session ids equal their names; the output of a session is `out of <name>`. `WindowKeys` stands on a
 * focused `Box` (see `showWindow`).
 */
@OptIn(ExperimentalTestApi::class)
class SessionPreviewWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")

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
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(agent.id, agent.dir)),
                sessions = list.toList(),
                outputs = list.associate { it.id to "out of ${it.name}" },
            ),
        )

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.selectSession(name: String) {
        await(WindowTags.missionRow(name)).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
    }

    private fun ComposeUiTest.assertPreviewShows(output: String) {
        await(WindowTags.PREVIEW)
        waitUntil { onAllNodes(hasText(output, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(WindowTags.PREVIEW_TEXT).assertIsDisplayed()
    }

    @Test
    fun `FR-K2 space shows the output of the selected session`() =
        runComposeUiTest {
            // Given a selected project with two working sessions, one selected
            val fake = sessions(session("older-work", 0), session("newer-work", 60))
            showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("newer-work")

            // When the user presses space
            press(Key.Spacebar)

            // Then the preview shows the output of the selected session only
            assertPreviewShows("out of newer-work")
            onAllNodes(hasText("out of older-work", substring = true)).assertCountEquals(0)
            fake.outputReads.toSet() shouldBe setOf(SessionId("newer-work"))
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-K2 j with the preview open shows the next session`() =
        runComposeUiTest {
            // Given an open preview of the first row (newer-work)
            val fake = sessions(session("older-work", 0), session("newer-work", 60))
            showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("newer-work")
            press(Key.Spacebar)
            assertPreviewShows("out of newer-work")

            // When the user presses j
            press(Key.J)

            // Then the preview follows the selection to the next row
            assertPreviewShows("out of older-work")
            onAllNodes(hasText("out of newer-work", substring = true)).assertCountEquals(0)
        }

    @Test
    fun `FR-K2 space again closes the preview`() =
        runComposeUiTest {
            // Given an open preview
            showWindow(FakeProjectsApi(initial = listOf(agent)), sessions(session("fix-login", 0)))
            await(WindowTags.projectRow("project0001")).performClick()
            selectSession("fix-login")
            press(Key.Spacebar)
            await(WindowTags.PREVIEW)

            // When the user presses space again
            press(Key.Spacebar)

            // Then the preview is gone
            waitUntilDoesNotExist(hasTestTag(WindowTags.PREVIEW))
        }

    @Test
    fun `FR-K1 palette offers the preview for a selected session only`() =
        runComposeUiTest {
            // Given a selected project with a session, none selected
            val fake = sessions(session("fix-login", 0))
            val palette = showWindow(FakeProjectsApi(initial = listOf(agent)), fake)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the palette opens
            runOnIdle { palette.open() }

            // Then the preview is not offered
            await(WindowTags.paletteRow("sessions.next"))
            onNodeWithTag(WindowTags.paletteRow("sessions.preview")).assertDoesNotExist()

            // When the session is selected and the palette opens again
            runOnIdle { palette.close() }
            selectSession("fix-login")
            runOnIdle { palette.open() }

            // Then "Podgląd outputu" is offered and picking it opens the preview
            await(WindowTags.paletteRow("sessions.preview")).performClick()
            assertPreviewShows("out of fix-login")

            // When the palette opens once more
            runOnIdle { palette.open() }

            // Then the same command now closes the preview
            await(WindowTags.paletteRow("sessions.preview")).assertIsDisplayed()
            onAllNodes(hasText("Zamknij podgląd", substring = true)).assertCountEquals(1)
        }
}
