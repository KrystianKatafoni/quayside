package dev.agentmanager.missioncontrol.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import dev.agentmanager.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * Global views on the real screen on fakes (FR-S9 second half, FR-S4; docs/12 §4): `GlobalViews` next to
 * `MissionControl`, the way `app` puts them. The controller runs in the composition scope with a one-hour tick.
 */
@OptIn(ExperimentalTestApi::class)
internal class GlobalViewsScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other"), displayName = "Other")

    private fun fresh(vararg sessions: Session): SessionsSnapshot =
        SessionsSnapshot(sessions.sortedBy { it.startedAt }, SnapshotStatus.Fresh(T0))

    /** Three waiting, two working and one failed session, in two projects and outside any project. */
    private fun everywhere(): SessionsSnapshot =
        fresh(
            aSession(id = "mine-ask", project = project.id, state = SessionState.AwaitingInput("q"), startedAt = T0),
            aSession(
                id = "mine-work",
                project = project.id,
                state = SessionState.Working,
                startedAt = T0.plusSeconds(1),
            ),
            aSession(
                id = "other-perm",
                project = other.id,
                state = SessionState.AwaitingPermission("Bash"),
                startedAt = T0.plusSeconds(2),
            ),
            aSession(
                id = "other-work",
                project = other.id,
                state = SessionState.Working,
                startedAt = T0.plusSeconds(3),
            ),
            aSession(
                id = "none-ask",
                project = null,
                state = SessionState.AwaitingInput(null),
                startedAt = T0.plusSeconds(4),
            ),
            aSession(id = "none-fail", project = null, state = SessionState.Failed, startedAt = T0.plusSeconds(5)),
        )

    private fun ComposeUiTest.showViews(sessions: SessionsApi) {
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        sessions,
                        FakeProjectsApi(initial = listOf(project, other)),
                        MutableStateFlow(project.id),
                        TestClock(T0.plusSeconds(600)),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            Row {
                GlobalViews(controller)
                MissionControl(controller, Modifier.weight(weight = 1f))
            }
        }
    }

    private fun ComposeUiTest.showScripted(snapshot: SessionsSnapshot) =
        showViews(SessionsApi(ScriptedSessionMonitor(snapshot), FakeSessions()))

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

    private fun row(id: String): String = MissionControlTags.row(SessionId(id))

    private fun ComposeUiTest.open(view: GlobalView) {
        await(MissionControlTags.view(view)).performClick()
    }

    @Test
    fun `FR-S9 waiting view shows waiting sessions of every project`() =
        runComposeUiTest {
            // Given waiting, working and failed sessions in two projects and outside any project
            showScripted(everywhere())
            await(row("mine-ask"))

            // When the user picks "Czeka na mnie"
            open(GlobalView.WAITING)

            // Then the entry is marked as selected and the waiting sessions of every project are listed
            await(MissionControlTags.view(GlobalView.WAITING)).assertIsSelected()
            await(row("other-perm"))
            await(row("none-ask"))
            await(row("mine-ask"))
            // And the working and failed ones are not
            awaitGone(row("mine-work"))
            awaitGone(row("other-work"))
            awaitGone(row("none-fail"))
            // And the header names the view, says it covers every project and counts the sessions
            textIn(MissionControlTags.HEADER, text("view.waiting")).assertIsDisplayed()
            textIn(MissionControlTags.HEADER, text("header.global", "3")).assertIsDisplayed()
        }

    @Test
    fun `FR-S9 working and failed views`() =
        runComposeUiTest {
            // Given sessions in every state across projects
            showScripted(everywhere())

            // When the user picks "Pracuje", then the working sessions of both projects are listed
            open(GlobalView.WORKING)
            await(row("mine-work"))
            await(row("other-work"))
            awaitGone(row("mine-ask"))
            awaitGone(row("none-fail"))
            textIn(MissionControlTags.HEADER, text("view.working")).assertIsDisplayed()

            // When the user picks "Błędy", then only the failed session is listed
            open(GlobalView.FAILED)
            await(row("none-fail"))
            awaitGone(row("mine-work"))
            awaitGone(row("other-work"))
            textIn(MissionControlTags.HEADER, text("view.failed")).assertIsDisplayed()
        }

    @Test
    fun `FR-S9 global rows show the project column`() =
        runComposeUiTest {
            // Given working sessions in two projects and one without a project
            showScripted(
                fresh(
                    aSession(id = "mine", project = project.id, startedAt = T0),
                    aSession(id = "theirs", project = other.id, startedAt = T0.plusSeconds(1)),
                    aSession(id = "nowhere", project = null, startedAt = T0.plusSeconds(2)),
                ),
            )

            // When the user picks "Pracuje"
            open(GlobalView.WORKING)

            // Then each row names its project, or says there is none
            await(row("mine"))
            textIn(row("mine"), "Agent Manager").assertIsDisplayed()
            textIn(row("theirs"), "Other").assertIsDisplayed()
            textIn(row("nowhere"), text("row.noProject")).assertIsDisplayed()
        }

    @Test
    fun `FR-S9 global view has no filter tabs`() =
        runComposeUiTest {
            // Given the project page with its filter tabs
            showScripted(everywhere())
            SessionFilter.entries.forEach { await(MissionControlTags.filter(it)) }

            // When the user picks a global view
            open(GlobalView.WORKING)

            // Then the filter tabs are gone
            await(row("other-work"))
            SessionFilter.entries.forEach { awaitGone(MissionControlTags.filter(it)) }
        }

    @Test
    fun `FR-S9 entries show counts`() =
        runComposeUiTest {
            // Given three waiting, two working and one failed session over two projects and outside
            showScripted(everywhere())

            // Then each entry shows its title and its count over every project
            await(MissionControlTags.view(GlobalView.WAITING))
            textIn(MissionControlTags.view(GlobalView.WAITING), text("view.waiting")).assertIsDisplayed()
            textIn(MissionControlTags.view(GlobalView.WAITING), "3").assertIsDisplayed()
            textIn(MissionControlTags.view(GlobalView.WORKING), text("view.working")).assertIsDisplayed()
            textIn(MissionControlTags.view(GlobalView.WORKING), "2").assertIsDisplayed()
            textIn(MissionControlTags.view(GlobalView.FAILED), text("view.failed")).assertIsDisplayed()
            textIn(MissionControlTags.view(GlobalView.FAILED), "1").assertIsDisplayed()
        }

    @Test
    fun `FR-S4 stop from the working view`() =
        runComposeUiTest {
            // Given a working session of another project
            val fake =
                FakeSessions(
                    SessionsWorld(
                        projects = listOf(WorldProject(project.id, project.dir), WorldProject(other.id, other.dir)),
                        sessions =
                            listOf(
                                WorldSession(SessionId("theirs"), other.dir, SessionState.Working, startedAt = T0),
                                WorldSession(
                                    SessionId("mine"),
                                    A_PROJECT_DIR,
                                    SessionState.Working,
                                    startedAt = T0.plusSeconds(1),
                                ),
                            ),
                    ),
                    TestClock(T0),
                )
            showViews(SessionsApi(monitor = fake, commands = fake))
            open(GlobalView.WORKING)

            // When the user opens the session and clicks "Zatrzymaj"
            await(row("theirs")).performClick()
            await(MissionControlTags.DETAILS_STOP).performClick()

            // Then that session is stopped and leaves the working view; the other one stays
            awaitGone(row("theirs"))
            fake.calls shouldBe listOf("stop theirs")
            await(row("mine"))
            await(MissionControlTags.DETAILS_EMPTY)
        }
}
