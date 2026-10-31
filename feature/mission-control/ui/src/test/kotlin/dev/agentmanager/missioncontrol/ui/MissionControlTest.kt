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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.sessions.api.aSession
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * User scenarios of Mission Control on fakes (FR-S1, FR-S2, FR-S8, FR-S9 first half, docs/12 §4). They live
 * here until `tests:functional` exists (AM-904). The controller runs in the composition scope, like the window
 * scope, with a one-hour tick so the age loop never keeps the UI test busy.
 */
@OptIn(ExperimentalTestApi::class)
internal class MissionControlTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    private val clock = TestClock(T0.plusSeconds(600))

    private fun fresh(vararg sessions: Session): SessionsSnapshot =
        SessionsSnapshot(sessions.sortedBy { it.startedAt }, SnapshotStatus.Fresh(T0))

    private fun ComposeUiTest.showMissionControl(
        snapshot: SessionsSnapshot,
        projects: List<Project> = listOf(project, other),
        selected: ProjectId? = project.id,
    ): ScriptedSessionMonitor {
        val monitor = ScriptedSessionMonitor(snapshot)
        val api = FakeProjectsApi(initial = projects)
        val selection = MutableStateFlow(selected)
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(monitor, FakeSessions()),
                        api,
                        selection,
                        clock,
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            MissionControl(controller)
        }
        return monitor
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    /** A text inside the node tagged [tag]: on a descendant, or on the node itself when it merges its children. */
    private fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

    private fun text(
        key: String,
        vararg args: String,
    ): String = MissionControlStrings.format(UiText(key, args.toList()))

    private fun row(id: String): String = MissionControlTags.row(SessionId(id))

    @Test
    fun `no projects shows the empty state`() =
        runComposeUiTest {
            // Given no projects
            showMissionControl(fresh(), projects = emptyList(), selected = null)

            // Then Mission Control says there are none and how to add one
            await(MissionControlTags.EMPTY)
            textIn(MissionControlTags.EMPTY, "Brak projektów").assertIsDisplayed()
            textIn(MissionControlTags.EMPTY, text("empty.noProjects.hint")).assertIsDisplayed()
        }

    @Test
    fun `no selected project asks to pick one`() =
        runComposeUiTest {
            // Given projects but none selected in the sidebar
            showMissionControl(fresh(aSession(project = project.id)), selected = null)

            // Then Mission Control asks to pick a project
            await(MissionControlTags.EMPTY)
            textIn(MissionControlTags.EMPTY, "Wybierz projekt").assertIsDisplayed()
        }

    @Test
    fun `FR-S1 project sessions show state name summary last event and age`() =
        runComposeUiTest {
            // Given a project with a session asking for Bash, last active 2 minutes ago, and a session elsewhere
            val asking =
                aSession(
                    id = "asking",
                    project = project.id,
                    name = "fix-login",
                    state = SessionState.AwaitingPermission("Bash"),
                    startedAt = T0,
                    lastActivityAt = T0.plusSeconds(480),
                    summary = "Fixing the login form",
                )
            val elsewhere = aSession(id = "elsewhere", project = other.id, name = "other-work")
            showMissionControl(fresh(asking, elsewhere))

            // Then the header names the project, its path and the number of sessions
            await(MissionControlTags.HEADER)
            textIn(MissionControlTags.HEADER, "Agent Manager").assertIsDisplayed()
            textIn(MissionControlTags.HEADER, A_PROJECT_DIR.toString()).assertIsDisplayed()
            textIn(MissionControlTags.HEADER, text("header.sessions", "1")).assertIsDisplayed()

            // And the row shows glyph, name, summary, last event and age
            await(row("asking"))
            textIn(row("asking"), "!").assertIsDisplayed()
            textIn(row("asking"), "fix-login").assertIsDisplayed()
            textIn(row("asking"), "Fixing the login form").assertIsDisplayed()
            textIn(row("asking"), text("event.permission", "Bash")).assertIsDisplayed()
            textIn(row("asking"), text("age.min", "2")).assertIsDisplayed()

            // And the session of another project is not listed
            awaitGone(row("elsewhere"))
        }

    @Test
    fun `FR-S2 each state shows its glyph`() =
        runComposeUiTest {
            // Given one session in every state
            val glyphs =
                mapOf(
                    "input" to (SessionState.AwaitingInput("q") to "?"),
                    "permission" to (SessionState.AwaitingPermission("Bash") to "!"),
                    "working" to (SessionState.Working to "~"),
                    "done" to (SessionState.Done to "✓"),
                    "failed" to (SessionState.Failed to "✕"),
                    "stopped" to (SessionState.Stopped to "○"),
                )
            val sessions =
                glyphs.entries.mapIndexed { i, (id, value) ->
                    aSession(id = id, project = project.id, state = value.first, startedAt = T0.plusSeconds(i.toLong()))
                }
            showMissionControl(fresh(*sessions.toTypedArray()))

            // Then every row shows the glyph of its state
            glyphs.forEach { (id, value) ->
                await(row(id))
                textIn(row(id), value.second).assertIsDisplayed()
            }
        }

    @Test
    fun `FR-S8 clicking a session shows its details`() =
        runComposeUiTest {
            // Given a project with a session and nothing selected
            val session =
                aSession(
                    id = "7c5dcf5d",
                    project = project.id,
                    name = "fix-login",
                    state = SessionState.Done,
                    summary = "Done with the login form",
                )
            showMissionControl(fresh(session))
            await(MissionControlTags.DETAILS_EMPTY)
            textIn(MissionControlTags.DETAILS_EMPTY, "Wybierz sesję").assertIsDisplayed()

            // When the user clicks the session
            await(row("7c5dcf5d")).performClick()

            // Then the panel shows its name, state, summary, worktree and id
            await(MissionControlTags.DETAILS)
            awaitGone(MissionControlTags.DETAILS_EMPTY)
            textIn(MissionControlTags.DETAILS, "fix-login").assertIsDisplayed()
            textIn(MissionControlTags.DETAILS, "✓").assertIsDisplayed()
            textIn(MissionControlTags.DETAILS, text("state.done")).assertIsDisplayed()
            textIn(MissionControlTags.DETAILS, "Done with the login form").assertIsDisplayed()
            textIn(MissionControlTags.DETAILS, session.cwd.toString()).assertIsDisplayed()
            textIn(MissionControlTags.DETAILS, "7c5dcf5d").assertIsDisplayed()
        }

    @Test
    fun `FR-S9 active and finished filters`() =
        runComposeUiTest {
            // Given a working and a finished session
            val working = aSession(id = "working", project = project.id, state = SessionState.Working)
            val done =
                aSession(id = "done", project = project.id, state = SessionState.Done, startedAt = T0.plusSeconds(1))
            showMissionControl(fresh(working, done))
            await(row("working"))
            await(row("done"))

            // When the user picks "Aktywne", then only the working session is listed
            await(MissionControlTags.filter(SessionFilter.ACTIVE)).performClick()
            awaitGone(row("done"))
            await(row("working"))

            // When the user picks "Zakończone", then only the finished session is listed
            await(MissionControlTags.filter(SessionFilter.FINISHED)).performClick()
            awaitGone(row("working"))
            await(row("done"))

            // When the user picks "Wszystkie", then both are listed again
            await(MissionControlTags.filter(SessionFilter.ALL)).performClick()
            await(row("working"))
            await(row("done"))
        }

    @Test
    fun `stale snapshot shows a banner and keeps the list`() =
        runComposeUiTest {
            // Given a project with a session listed while Claude Code answered
            val session = aSession(id = "kept", project = project.id)
            val monitor = showMissionControl(fresh(session))
            await(row("kept"))

            // When the next refresh times out
            monitor.show(
                SessionsSnapshot(listOf(session), SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)),
            )

            // Then a banner says since when the list is stale, and the list stays
            await(MissionControlTags.BANNER)
            textIn(MissionControlTags.BANNER, "Nieaktualne od").assertIsDisplayed()
            await(row("kept"))
        }

    @Test
    fun `loading snapshot shows loading`() =
        runComposeUiTest {
            // Given sessions not listed yet
            val monitor = showMissionControl(SessionsSnapshot.Initial)

            // Then Mission Control says it is loading
            await(MissionControlTags.LOADING)
            textIn(MissionControlTags.LOADING, "Wczytywanie sesji…").assertIsDisplayed()

            // When the first listing arrives, then the loading text goes away and the session is listed
            monitor.show(fresh(aSession(id = "first", project = project.id)))
            awaitGone(MissionControlTags.LOADING)
            await(row("first"))
        }

    @Test
    fun `mode bar shows NORMAL project and counters`() =
        runComposeUiTest {
            // Given waiting and working sessions in two projects
            showMissionControl(
                fresh(
                    aSession(id = "q1", project = project.id, state = SessionState.AwaitingInput("q")),
                    aSession(id = "p1", project = other.id, state = SessionState.AwaitingPermission("Bash")),
                    aSession(id = "w1", project = other.id, state = SessionState.Working),
                    aSession(id = "d1", project = project.id, state = SessionState.Done),
                ),
            )

            // Then the mode bar shows the mode, the selected project and the counters of every project
            await(MissionControlTags.MODE_BAR)
            textIn(MissionControlTags.MODE_BAR, "NORMAL").assertIsDisplayed()
            textIn(MissionControlTags.MODE_BAR, "Agent Manager").assertIsDisplayed()
            textIn(MissionControlTags.MODE_BAR, text("modeBar.waiting", "2")).assertIsDisplayed()
            textIn(MissionControlTags.MODE_BAR, text("modeBar.working", "1")).assertIsDisplayed()
            textIn(MissionControlTags.MODE_BAR, text("fresh.live")).assertIsDisplayed()
        }
}
