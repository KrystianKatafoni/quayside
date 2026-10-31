package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * Stop and Respawn buttons of the details panel on fakes (FR-S4, AM-410; docs/12 §4). The controller runs in the
 * composition scope with a one-hour tick. Commands finish at once unless the test uses [GatedCommands].
 */
@OptIn(ExperimentalTestApi::class)
internal class SessionActionsScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val secretName = "secret-feature-name"

    private fun fake(state: SessionState): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions =
                    listOf(
                        WorldSession(
                            id = SessionId("7c5dcf5d"),
                            cwd = A_PROJECT_DIR,
                            state = state,
                            name = secretName,
                            startedAt = T0,
                        ),
                    ),
            ),
            TestClock(T0),
        )

    private fun ComposeUiTest.showMissionControl(
        sessions: FakeSessions,
        commands: SessionCommands = sessions,
    ) {
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(monitor = sessions, commands = commands),
                        FakeProjectsApi(initial = listOf(project)),
                        MutableStateFlow(project.id),
                        TestClock(T0.plusSeconds(60)),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            MissionControl(controller)
        }
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    private fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

    private fun text(
        key: String,
        vararg args: String,
    ): String = MissionControlStrings.format(UiText(key, args.toList()))

    /** The button is either not shown or shown disabled; both mean "cannot be used now". */
    private fun ComposeUiTest.assertUnavailable(tag: String) {
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) onNodeWithTag(tag).assertIsNotEnabled()
    }

    private fun ComposeUiTest.selectSession() {
        await(MissionControlTags.row(SessionId("7c5dcf5d"))).performClick()
        await(MissionControlTags.DETAILS)
    }

    @Test
    fun `FR-S4 stop button stops a working session`() =
        runComposeUiTest {
            // Given a working session, selected
            val sessions = fake(SessionState.Working)
            showMissionControl(sessions)
            selectSession()
            await(MissionControlTags.DETAILS_STOP).assertIsEnabled()
            awaitGone(MissionControlTags.DETAILS_RESPAWN)

            // When the user clicks "Zatrzymaj"
            onNodeWithTag(MissionControlTags.DETAILS_STOP).performClick()

            // Then the session is stopped and the panel offers Respawn instead
            await(MissionControlTags.DETAILS_RESPAWN)
            awaitGone(MissionControlTags.DETAILS_STOP)
            sessions.calls shouldBe listOf("stop 7c5dcf5d")
            textIn(MissionControlTags.DETAILS, text("state.stopped")).assertIsDisplayed()
        }

    @Test
    fun `FR-S4 respawn button brings back a stopped session`() =
        runComposeUiTest {
            // Given a stopped session, selected
            val sessions = fake(SessionState.Stopped)
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Respawn"
            await(MissionControlTags.DETAILS_RESPAWN).performClick()

            // Then the session works again and the panel offers Stop
            await(MissionControlTags.DETAILS_STOP)
            awaitGone(MissionControlTags.DETAILS_RESPAWN)
            sessions.calls shouldBe listOf("respawn 7c5dcf5d")
            textIn(MissionControlTags.DETAILS, text("state.working")).assertIsDisplayed()
        }

    @Test
    fun `FR-S4 a finished session shows respawn and no stop`() =
        runComposeUiTest {
            // Given a finished session, selected
            showMissionControl(fake(SessionState.Done))
            selectSession()

            // Then only Respawn is offered
            await(MissionControlTags.DETAILS_RESPAWN).assertIsEnabled()
            awaitGone(MissionControlTags.DETAILS_STOP)
        }

    @Test
    fun `FR-S4 hides the buttons while an action runs`() =
        runComposeUiTest {
            // Given a working session whose stop does not finish yet
            val sessions = fake(SessionState.Working)
            val gated = GatedCommands(sessions)
            showMissionControl(sessions, gated)
            selectSession()

            // When the user clicks "Zatrzymaj"
            await(MissionControlTags.DETAILS_STOP).performClick()

            // Then the panel says the session is being stopped and no button can be used
            await(MissionControlTags.DETAILS_BUSY)
            textIn(MissionControlTags.DETAILS_BUSY, text("action.stop.busy")).assertIsDisplayed()
            assertUnavailable(MissionControlTags.DETAILS_STOP)
            assertUnavailable(MissionControlTags.DETAILS_RESPAWN)
            gated.started shouldBe listOf("stop 7c5dcf5d")

            // When the command finishes
            runOnIdle { gated.gate.complete(Unit) }

            // Then the busy text goes and Respawn is offered
            awaitGone(MissionControlTags.DETAILS_BUSY)
            await(MissionControlTags.DETAILS_RESPAWN).assertIsEnabled()
        }

    @Test
    fun `FR-S4 a failed stop shows a fixed text without the session name`() =
        runComposeUiTest {
            // Given a working session that cannot be stopped
            val sessions = fake(SessionState.Working)
            sessions.failStop(SessionId("7c5dcf5d"))
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Zatrzymaj"
            await(MissionControlTags.DETAILS_STOP).performClick()

            // Then the panel shows the fixed failure title and reason, and the session keeps running
            await(MissionControlTags.DETAILS_FAILURE)
            textIn(MissionControlTags.DETAILS_FAILURE, text("action.stop.failed")).assertIsDisplayed()
            textIn(MissionControlTags.DETAILS_FAILURE, text("action.reason.runtime.COMMAND_FAILED")).assertIsDisplayed()
            val leaks =
                onAllNodes(
                    hasText(secretName, substring = true) and
                        (
                            hasTestTag(MissionControlTags.DETAILS_FAILURE) or
                                hasAnyAncestor(hasTestTag(MissionControlTags.DETAILS_FAILURE))
                        ),
                ).fetchSemanticsNodes()
            leaks.size shouldBe 0
            await(MissionControlTags.DETAILS_STOP).assertIsEnabled()
        }

    @Test
    fun `FR-S4 remove asks for confirmation and removes the session`() =
        runComposeUiTest {
            // Given a finished session, selected
            val sessions = fake(SessionState.Done)
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Usuń z listy"
            await(MissionControlTags.DETAILS_REMOVE).performClick()

            // Then a confirmation names the session and nothing is removed yet
            await(MissionControlTags.CONFIRM_REMOVE)
            textIn(MissionControlTags.CONFIRM_REMOVE, text("confirmRemove.title")).assertIsDisplayed()
            textIn(MissionControlTags.CONFIRM_REMOVE, secretName).assertIsDisplayed()
            sessions.calls.size shouldBe 0

            // When the user confirms
            await(MissionControlTags.CONFIRM_REMOVE_OK).performClick()

            // Then the dialog closes, the session leaves the list and the panel asks to pick a session
            awaitGone(MissionControlTags.CONFIRM_REMOVE)
            awaitGone(MissionControlTags.row(SessionId("7c5dcf5d")))
            await(MissionControlTags.DETAILS_EMPTY)
            sessions.calls shouldBe listOf("remove 7c5dcf5d")
        }

    @Test
    fun `FR-S4 cancel keeps the session`() =
        runComposeUiTest {
            // Given the confirmation of a finished session
            val sessions = fake(SessionState.Done)
            showMissionControl(sessions)
            selectSession()
            await(MissionControlTags.DETAILS_REMOVE).performClick()
            await(MissionControlTags.CONFIRM_REMOVE)

            // When the user clicks "Anuluj"
            await(MissionControlTags.CONFIRM_REMOVE_CANCEL).performClick()

            // Then the dialog closes and the session stays
            awaitGone(MissionControlTags.CONFIRM_REMOVE)
            await(MissionControlTags.DETAILS_REMOVE).assertIsEnabled()
            onNodeWithTag(MissionControlTags.row(SessionId("7c5dcf5d"))).assertIsDisplayed()
            sessions.calls.size shouldBe 0
        }

    @Test
    fun `FR-S4 escape closes the confirmation`() =
        runComposeUiTest {
            // Given the confirmation of a finished session
            val sessions = fake(SessionState.Done)
            showMissionControl(sessions)
            selectSession()
            await(MissionControlTags.DETAILS_REMOVE).performClick()

            // When the user presses Escape in the dialog
            await(MissionControlTags.CONFIRM_REMOVE).performKeyInput {
                keyDown(Key.Escape)
                keyUp(Key.Escape)
            }

            // Then the dialog closes and the session stays
            awaitGone(MissionControlTags.CONFIRM_REMOVE)
            onNodeWithTag(MissionControlTags.row(SessionId("7c5dcf5d"))).assertIsDisplayed()
            sessions.calls.size shouldBe 0
        }

    @Test
    fun `FR-S4 a working session shows no remove`() =
        runComposeUiTest {
            // Given a working session, selected
            showMissionControl(fake(SessionState.Working))
            selectSession()

            // Then Stop is offered and Remove is not
            await(MissionControlTags.DETAILS_STOP).assertIsEnabled()
            awaitGone(MissionControlTags.DETAILS_REMOVE)
        }

    @Test
    fun `FR-S4 a failed remove shows a fixed text without the session name`() =
        runComposeUiTest {
            // Given a finished session whose removal fails
            val sessions = fake(SessionState.Done)
            sessions.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Usuń z listy" and confirms
            await(MissionControlTags.DETAILS_REMOVE).performClick()
            await(MissionControlTags.CONFIRM_REMOVE_OK).performClick()

            // Then the panel shows the fixed failure title and reason, and the session stays
            await(MissionControlTags.DETAILS_FAILURE)
            textIn(MissionControlTags.DETAILS_FAILURE, text("action.remove.failed")).assertIsDisplayed()
            textIn(MissionControlTags.DETAILS_FAILURE, text("action.reason.runtime.COMMAND_FAILED")).assertIsDisplayed()
            val leaks =
                onAllNodes(
                    hasText(secretName, substring = true) and
                        (
                            hasTestTag(MissionControlTags.DETAILS_FAILURE) or
                                hasAnyAncestor(hasTestTag(MissionControlTags.DETAILS_FAILURE))
                        ),
                ).fetchSemanticsNodes()
            leaks.size shouldBe 0
            await(MissionControlTags.DETAILS_REMOVE).assertIsEnabled()
        }

    @Test
    fun `action buttons do not take the focus when clicked`() =
        runComposeUiTest {
            // Given a working session whose stop fails (so the Stop button stays), selected
            val sessions = fake(SessionState.Working)
            sessions.failStop(SessionId("7c5dcf5d"))
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Zatrzymaj"
            await(MissionControlTags.DETAILS_STOP).performClick()
            await(MissionControlTags.DETAILS_FAILURE)

            // Then the button does not hold the focus, so a later Enter would not click it again
            onNodeWithTag(MissionControlTags.DETAILS_STOP).assertIsNotFocused()

            // When the user opens the preview and closes it with its own button
            await(MissionControlTags.DETAILS_PREVIEW).performClick()
            onNodeWithTag(MissionControlTags.DETAILS_PREVIEW).assertIsNotFocused()
            await(MissionControlTags.PREVIEW_CLOSE).performClick()

            // Then neither button holds the focus
            awaitGone(MissionControlTags.PREVIEW_CLOSE)
            onNodeWithTag(MissionControlTags.DETAILS_PREVIEW).assertIsNotFocused()
        }
}
