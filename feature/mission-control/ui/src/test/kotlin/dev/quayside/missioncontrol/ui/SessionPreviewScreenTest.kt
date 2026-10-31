package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMonitor
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
 * The output preview under the session list on fakes (FR-K2, AM-411; docs/12 §4). The controller runs in the
 * composition scope with a one-hour tick. Reads finish at once unless the test uses [GatedOutput].
 */
@OptIn(ExperimentalTestApi::class)
internal class SessionPreviewScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val id = SessionId("7c5dcf5d")
    private val secretName = "secret-feature-name"
    private val output = "Editing LoginForm.kt\nAll tests pass"

    private fun fake(output: String = this.output): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions =
                    listOf(
                        WorldSession(
                            id = id,
                            cwd = A_PROJECT_DIR,
                            state = SessionState.Working,
                            name = secretName,
                            startedAt = T0,
                        ),
                    ),
                outputs = mapOf(id to output),
            ),
            TestClock(T0),
        )

    private fun ComposeUiTest.showMissionControl(
        sessions: FakeSessions,
        monitor: SessionMonitor = sessions,
    ) {
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(monitor = monitor, commands = sessions),
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

    private fun ComposeUiTest.selectSession() {
        await(MissionControlTags.row(id)).performClick()
        await(MissionControlTags.DETAILS)
    }

    @Test
    fun `FR-K2 preview button shows the output of the session`() =
        runComposeUiTest {
            // Given a working session with some output, selected; no preview yet
            val sessions = fake()
            showMissionControl(sessions)
            selectSession()
            onAllNodesWithTag(MissionControlTags.PREVIEW).fetchSemanticsNodes().size shouldBe 0
            textIn(MissionControlTags.DETAILS_PREVIEW, text("action.preview")).assertIsDisplayed()

            // When the user clicks "Podgląd"
            await(MissionControlTags.DETAILS_PREVIEW).performClick()

            // Then the panel shows the session name in the title and the output text
            await(MissionControlTags.PREVIEW)
            textIn(MissionControlTags.PREVIEW, text("preview.title", secretName)).assertIsDisplayed()
            textIn(MissionControlTags.PREVIEW_TEXT, "All tests pass").assertIsDisplayed()
            sessions.outputReads.toSet() shouldBe setOf(id)
            sessions.calls.size shouldBe 0
        }

    @Test
    fun `FR-K2 preview shows loading then the text`() =
        runComposeUiTest {
            // Given a session whose output read does not finish yet
            val sessions = fake()
            val gated = GatedOutput(sessions)
            showMissionControl(sessions, gated)
            selectSession()
            gated.hold()

            // When the user clicks "Podgląd"
            await(MissionControlTags.DETAILS_PREVIEW).performClick()

            // Then the panel says the output is loading
            await(MissionControlTags.PREVIEW)
            textIn(MissionControlTags.PREVIEW, text("preview.loading")).assertIsDisplayed()

            // When the read finishes
            runOnIdle { gated.release() }

            // Then the loading text goes and the output is shown
            await(MissionControlTags.PREVIEW_TEXT)
            waitUntilDoesNotExist(hasText(text("preview.loading"), substring = true))
            textIn(MissionControlTags.PREVIEW_TEXT, "Editing LoginForm.kt").assertIsDisplayed()
        }

    @Test
    fun `FR-K2 empty output shows a fixed text`() =
        runComposeUiTest {
            // Given a session that printed nothing yet, selected
            showMissionControl(fake(output = ""))
            selectSession()

            // When the user clicks "Podgląd"
            await(MissionControlTags.DETAILS_PREVIEW).performClick()

            // Then the panel shows the fixed "no output" text
            await(MissionControlTags.PREVIEW)
            textIn(MissionControlTags.PREVIEW, text("preview.empty")).assertIsDisplayed()
        }

    @Test
    fun `FR-K2 a failed read shows a fixed text instead of the output`() =
        runComposeUiTest {
            // Given a session whose output cannot be read
            val sessions = fake()
            sessions.failNextOutput(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            showMissionControl(sessions)
            selectSession()

            // When the user clicks "Podgląd"
            await(MissionControlTags.DETAILS_PREVIEW).performClick()

            // Then the panel shows the fixed failure text and reason, and none of the output
            await(MissionControlTags.PREVIEW)
            textIn(MissionControlTags.PREVIEW, text("preview.failed")).assertIsDisplayed()
            textIn(MissionControlTags.PREVIEW, text("action.reason.runtime.COMMAND_FAILED")).assertIsDisplayed()
            onAllNodes(hasText("All tests pass", substring = true)).assertCountEquals(0)
        }

    @Test
    fun `FR-K2 close hides the preview`() =
        runComposeUiTest {
            // Given an open preview
            showMissionControl(fake())
            selectSession()
            await(MissionControlTags.DETAILS_PREVIEW).performClick()
            await(MissionControlTags.PREVIEW)
            textIn(MissionControlTags.PREVIEW_CLOSE, text("preview.close")).assertIsDisplayed()

            // When the user clicks "Zamknij"
            await(MissionControlTags.PREVIEW_CLOSE).performClick()

            // Then the panel is gone and the session list stays
            awaitGone(MissionControlTags.PREVIEW)
            onNodeWithTag(MissionControlTags.row(id)).assertIsDisplayed()
        }
}
