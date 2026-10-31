package dev.agentmanager.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.sessions.api.aSession
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** Intents from the window keys on the real screen (FR-K2, AM-409); the keys themselves are tested in `app`. */
@OptIn(ExperimentalTestApi::class)
internal class SessionIntentScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")

    private fun ComposeUiTest.showMissionControl(): MissionControlController {
        val older = aSession(id = "older", project = project.id, name = "older-work", startedAt = T0)
        val newer = aSession(id = "newer", project = project.id, name = "newer-work", startedAt = T0.plusSeconds(60))
        val monitor = ScriptedSessionMonitor(SessionsSnapshot(listOf(older, newer), SnapshotStatus.Fresh(T0)))
        val built = mutableListOf<MissionControlController>()
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
                    ).also { built += it }
                }
            MissionControl(controller)
        }
        waitUntil { built.isNotEmpty() }
        return built.single()
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    @Test
    fun `FR-K2 next session intent selects the first row and shows its details`() =
        runComposeUiTest {
            // Given a project with two sessions and nothing selected
            val controller = showMissionControl()
            await(MissionControlTags.DETAILS_EMPTY)

            // When the next-session intent arrives
            runOnIdle { controller.onIntent(MissionControlIntent.NextSession) }

            // Then the first row (the newest session) is open in the details panel
            await(MissionControlTags.DETAILS)
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.DETAILS_EMPTY))
            onAllNodes(
                hasText("newer-work", substring = true) and hasAnyAncestor(hasTestTag(MissionControlTags.DETAILS)),
            ).onFirst()
                .assertIsDisplayed()

            // And the next intent moves to the second row
            runOnIdle { controller.onIntent(MissionControlIntent.NextSession) }
            waitUntil {
                onAllNodes(
                    hasText("older-work", substring = true) and hasAnyAncestor(hasTestTag(MissionControlTags.DETAILS)),
                ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
}
