package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionState.AwaitingInput
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * The mouse paths to the terminal and to the session actions (FR-K3, AM-511, ADR 0015): Attach and Odpowiedz buttons,
 * the row menu (right click and "⋯"), a double click and the cheat sheet. The callbacks stand for `app`'s attacher.
 */
@OptIn(ExperimentalTestApi::class)
internal class SessionOpenScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val working = SessionId("working1")
    private val asking = SessionId("asking01")
    private val finished = SessionId("finished")
    private val opened = mutableListOf<String>()

    private fun ComposeUiTest.show(): ScriptedSessionMonitor {
        val sessions =
            listOf(
                aSession("working1", project.id, SessionState.Working, name = "work", startedAt = T0.plusSeconds(3)),
                aSession("asking01", project.id, AwaitingInput(null), name = "ask", startedAt = T0.plusSeconds(2)),
                aSession("finished", project.id, SessionState.Done, name = "done", startedAt = T0.plusSeconds(1)),
            )
        val monitor = ScriptedSessionMonitor(SessionsSnapshot(sessions, SnapshotStatus.Fresh(T0)))
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(monitor, FakeSessions()),
                        FakeProjectsApi(initial = listOf(project)),
                        MutableStateFlow(project.id),
                        TestClock(T0.plusSeconds(600)),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            MissionControl(controller, onAttach = { opened += "attach" }, onReply = { opened += "reply" })
        }
        return monitor
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.row(id: SessionId) = await(MissionControlTags.row(id))

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.select(id: SessionId) {
        row(id).performClick()
        await(MissionControlTags.DETAILS)
        waitForIdle()
    }

    @Test
    fun `FR-K3 the Attach button of a working session attaches and has no Odpowiedz`() =
        runComposeUiTest {
            // Given a working session, selected
            show()
            select(working)

            // When the user clicks Attach
            await(MissionControlTags.DETAILS_ATTACH).performClick()

            // Then the window is asked to attach, the button keeps no focus (space and Enter stay window keys),
            // and a working session cannot be replied to
            opened shouldBe listOf("attach")
            onNodeWithTag(MissionControlTags.DETAILS_ATTACH).assertIsNotFocused()
            has(MissionControlTags.DETAILS_REPLY) shouldBe false
        }

    @Test
    fun `FR-K3 the Odpowiedz button of a waiting session replies`() =
        runComposeUiTest {
            // Given a session that waits for an answer, selected
            show()
            select(asking)

            // When the user clicks Odpowiedz
            await(MissionControlTags.DETAILS_REPLY).performClick()

            // Then the window is asked to reply
            opened shouldBe listOf("reply")
        }

    @Test
    fun `FR-K3 a double click selects the row and attaches, a single click only selects`() =
        runComposeUiTest {
            // Given nothing selected
            show()

            // When the user clicks a row once
            row(finished).performMouseInput { click() }
            await(MissionControlTags.DETAILS)

            // Then it is selected and nothing is attached
            opened shouldBe emptyList()

            // And a double click on another row selects it and attaches
            row(asking).performMouseInput { doubleClick() }
            waitForIdle()
            opened shouldBe listOf("attach")
            await(MissionControlTags.DETAILS_REPLY)
        }

    @Test
    fun `FR-K3 a right click selects the row and opens the menu of its session`() =
        runComposeUiTest {
            // Given a project with a working, a waiting and a finished session, nothing selected
            show()

            // When the user right-clicks the waiting session
            row(asking).performMouseInput { rightClick() }

            // Then the menu offers attach, reply, preview and stop (it is active), and no respawn
            await(MissionControlTags.ROW_MENU)
            listOf("attach", "reply", "preview", "stop").forEach { await(MissionControlTags.menuItem(it)) }
            has(MissionControlTags.menuItem("respawn")) shouldBe false
            await(MissionControlTags.DETAILS_REPLY)
        }

    @Test
    fun `FR-K3 the button of a finished row opens its menu with respawn and remove`() =
        runComposeUiTest {
            // Given a finished session
            show()

            // When the user clicks its "⋯" button
            await(MissionControlTags.rowMenuButton(finished)).performClick()

            // Then the button keeps no focus, and the menu has attach, preview, respawn and remove, no reply or stop
            onNodeWithTag(MissionControlTags.rowMenuButton(finished)).assertIsNotFocused()
            await(MissionControlTags.ROW_MENU)
            listOf("attach", "preview", "respawn", "remove").forEach { await(MissionControlTags.menuItem(it)) }
            has(MissionControlTags.menuItem("reply")) shouldBe false
            has(MissionControlTags.menuItem("stop")) shouldBe false
        }

    @Test
    fun `FR-K3 a menu item runs its action and closes the menu`() =
        runComposeUiTest {
            // Given the open menu of a waiting session
            show()
            row(asking).performMouseInput { rightClick() }
            await(MissionControlTags.menuItem("reply")).performClick()

            // Then the window is asked to reply and the menu is gone
            opened shouldBe listOf("reply")
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.ROW_MENU))

            // And a menu item for preview opens the preview
            row(asking).performMouseInput { rightClick() }
            await(MissionControlTags.menuItem("preview")).performClick()
            await(MissionControlTags.PREVIEW)
        }

    @Test
    fun `FR-K3 Escape and another selection close the menu`() =
        runComposeUiTest {
            // Given an open menu
            show()
            row(working).performMouseInput { rightClick() }
            await(MissionControlTags.ROW_MENU)

            // When Escape is pressed
            onNodeWithTag(MissionControlTags.ROW_MENU).performKeyInput {
                keyDown(Key.Escape)
                keyUp(Key.Escape)
            }

            // Then it closes, and does not come back when the same row is selected again after another one
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.ROW_MENU))
            select(finished)
            select(working)
            has(MissionControlTags.ROW_MENU) shouldBe false
        }

    @Test
    fun `FR-K3 the cheat sheet is closed at first and the toggle opens and closes it`() =
        runComposeUiTest {
            // Given the window
            show()
            await(MissionControlTags.MODE_BAR)
            has(MissionControlTags.SHORTCUTS) shouldBe false

            // When the user clicks "Skróty"
            await(MissionControlTags.SHORTCUTS_TOGGLE).performClick()

            // Then the list of shortcuts shows, and a second click hides it
            await(MissionControlTags.SHORTCUTS)
            onNodeWithTag(MissionControlTags.SHORTCUTS_TOGGLE).assertIsNotFocused()
            await(MissionControlTags.SHORTCUTS_TOGGLE).performClick()
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.SHORTCUTS))
        }
}
