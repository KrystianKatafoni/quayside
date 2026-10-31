package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset

/** The whole Mission Control screen from the snapshot, the projects and the selection (pure, ENG-C9). */
internal class MissionControlReducerTest {
    private val project = aProject(displayName = "Quayside")
    private val other = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    private val nested = aProject(id = "nested", dir = A_PROJECT_DIR.resolve("tools"), displayName = "Tools")
    private val now: Instant = T0.plusSeconds(600)

    private fun fresh(vararg sessions: Session): SessionsSnapshot =
        SessionsSnapshot(sessions.sortedBy { it.startedAt }, SnapshotStatus.Fresh(T0))

    private fun reduce(
        snapshot: SessionsSnapshot,
        projects: List<Project> = listOf(project, other),
        selection: Selection = Selection(project = project.id),
    ): MissionControlUiState = MissionControlReducer.reduce(snapshot, projects, selection, now, ZoneOffset.UTC)

    private fun MissionControlUiState.page(): Screen.Project = screen.shouldBeInstanceOf<Screen.Project>()

    private fun Screen.Project.ids(): List<String> = rows.map { it.id.value }

    @Test
    fun `no projects gives the empty screen`() {
        val state = reduce(fresh(aSession(project = null)), projects = emptyList(), selection = Selection())

        state.screen shouldBe Screen.NoProjects
        state.modeBar.project.shouldBeNull()
    }

    @Test
    fun `no selected project asks to pick one`() {
        val state = reduce(fresh(aSession()), selection = Selection())

        state.screen shouldBe Screen.NoSelection
        state.modeBar.project.shouldBeNull()
    }

    @Test
    fun `selected project that is no longer on the list asks to pick one`() {
        val state = reduce(fresh(aSession()), projects = listOf(other), selection = Selection(project = project.id))

        state.screen shouldBe Screen.NoSelection
    }

    @Test
    fun `shows only sessions of the selected project`() {
        val snapshot =
            fresh(
                aSession(id = "mine1", project = project.id),
                aSession(id = "theirs", project = other.id),
                aSession(id = "nowhere", project = null),
                aSession(id = "mine2", project = project.id, startedAt = T0.plusSeconds(1)),
            )

        val page = reduce(snapshot).page()

        page.ids().toSet() shouldBe setOf("mine1", "mine2")
        page.header shouldBe ProjectHeader(name = "Quayside", path = A_PROJECT_DIR, sessions = 2)
    }

    @Test
    fun `sessions of a nested project are not shown`() {
        val snapshot =
            fresh(
                aSession(id = "mine", project = project.id),
                aSession(id = "inner", project = nested.id, cwd = nested.dir),
            )

        val page = reduce(snapshot, projects = listOf(project, nested)).page()

        page.ids() shouldBe listOf("mine")
        page.header.sessions shouldBe 1
    }

    @Test
    fun `newest session is on top`() {
        val snapshot =
            fresh(
                aSession(id = "oldest", startedAt = T0),
                aSession(id = "middle", startedAt = T0.plusSeconds(60)),
                aSession(id = "newest", startedAt = T0.plusSeconds(120)),
            )

        reduce(snapshot).page().ids() shouldBe listOf("newest", "middle", "oldest")
    }

    @Test
    fun `row shows glyph name summary last event and age`() {
        val session =
            aSession(
                id = "asking",
                name = "fix-login",
                state = SessionState.AwaitingPermission("Bash"),
                startedAt = T0,
                lastActivityAt = T0.plusSeconds(480),
                summary = "Fixing the login form",
            )

        val row = reduce(fresh(session)).page().rows.single()

        row shouldBe
            SessionRow(
                id = SessionId("asking"),
                glyph = Glyph("!", Tone.WARN, "state.awaitingPermission"),
                name = "fix-login",
                summary = "Fixing the login form",
                lastEvent = UiText("event.permission", listOf("Bash")),
                // now is T0 + 10 min, last activity T0 + 8 min: the age counts from the last activity.
                age = UiText("age.min", listOf("2")),
                selected = false,
            )
    }

    @Test
    fun `active filter hides done failed and stopped`() {
        val page = reduce(everyState(), selection = Selection(project.id, filter = SessionFilter.ACTIVE)).page()

        page.ids().toSet() shouldBe setOf("working", "input", "permission")
        page.filter shouldBe SessionFilter.ACTIVE
        page.header.sessions shouldBe 6
    }

    @Test
    fun `finished filter shows only inactive sessions`() {
        val page = reduce(everyState(), selection = Selection(project.id, filter = SessionFilter.FINISHED)).page()

        page.ids().toSet() shouldBe setOf("done", "failed", "stopped")
    }

    @Test
    fun `all filter shows every session of the project`() {
        reduce(everyState()).page().ids().toSet() shouldBe
            setOf("working", "input", "permission", "done", "failed", "stopped")
    }

    @Test
    fun `selected session has details and a marked row`() {
        val session =
            aSession(
                id = "picked",
                name = "fix-login",
                state = SessionState.Done,
                startedAt = T0,
                lastActivityAt = T0.plusSeconds(30),
                summary = "Done with the login form",
            )
        val snapshot = fresh(session, aSession(id = "else", startedAt = T0.plusSeconds(1)))

        val page = reduce(snapshot, selection = Selection(project.id, session = SessionId("picked"))).page()

        page.details shouldBe
            SessionDetails(
                id = SessionId("picked"),
                name = "fix-login",
                glyph = Glyph("✓", Tone.OK, "state.done"),
                summary = "Done with the login form",
                worktree = session.cwd,
                startedAt = T0,
                lastActivityAt = T0.plusSeconds(30),
            )
        page.rows.filter { it.selected }.map { it.id.value } shouldBe listOf("picked")
    }

    @Test
    fun `selected session gone from the snapshot clears the details`() {
        val page =
            reduce(
                fresh(aSession(id = "left")),
                selection = Selection(project.id, session = SessionId("gone")),
            ).page()

        page.details.shouldBeNull()
        page.rows.none { it.selected } shouldBe true
    }

    @Test
    fun `selected session of another project is not shown in the details`() {
        val snapshot = fresh(aSession(id = "mine"), aSession(id = "theirs", project = other.id))

        val page = reduce(snapshot, selection = Selection(project.id, session = SessionId("theirs"))).page()

        page.details.shouldBeNull()
    }

    @Test
    fun `selected session hidden by the filter clears the details`() {
        val selection = Selection(project.id, filter = SessionFilter.ACTIVE, session = SessionId("done"))

        val page = reduce(everyState(), selection = selection).page()

        page.details.shouldBeNull()
        page.rows.none { it.selected } shouldBe true
    }

    @Test
    fun `fresh snapshot shows no banner and is not loading`() {
        val state = reduce(fresh(aSession()))

        state.page().banner.shouldBeNull()
        state.page().loading shouldBe false
        state.modeBar.freshness shouldBe UiText("fresh.live")
    }

    @Test
    fun `stale snapshot keeps the rows and shows the banner`() {
        val stale = SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)
        val snapshot = SessionsSnapshot(listOf(aSession(id = "kept")), stale)

        val state = reduce(snapshot)

        state.page().ids() shouldBe listOf("kept")
        state.page().banner shouldBe UiText("fresh.stale.TIMEOUT", listOf("09:00:05"))
        state.page().loading shouldBe false
        // The mode bar has only the short text: the banner names the problem.
        state.modeBar.freshness shouldBe UiText("fresh.short.stale", listOf("09:00:05"))
    }

    @Test
    fun `loading snapshot shows loading`() {
        val state = reduce(SessionsSnapshot.Initial)

        state.page().loading shouldBe true
        state.page().rows shouldBe emptyList()
        state.page().banner.shouldBeNull()
        state.modeBar.freshness shouldBe UiText("fresh.loading")
    }

    @Test
    fun `mode bar counts waiting and working sessions of every project`() {
        val snapshot =
            fresh(
                aSession(id = "w1", project = project.id, state = SessionState.Working),
                aSession(id = "w2", project = null, state = SessionState.Working),
                aSession(id = "q1", project = project.id, state = SessionState.AwaitingInput("q")),
                aSession(id = "p1", project = other.id, state = SessionState.AwaitingPermission("Bash")),
                aSession(id = "p2", project = null, state = SessionState.AwaitingPermission(null)),
                aSession(id = "d1", project = other.id, state = SessionState.Done),
                aSession(id = "f1", project = project.id, state = SessionState.Failed),
            )

        // The counters do not depend on the filter or on which project is selected.
        val state = reduce(snapshot, selection = Selection(project.id, filter = SessionFilter.FINISHED))

        state.modeBar shouldBe
            ModeBar(
                mode = "NORMAL",
                project = "Quayside",
                waiting = 3,
                working = 2,
                freshness = UiText("fresh.live"),
            )
    }

    @Test
    fun `mode bar counts sessions also without a selected project`() {
        val snapshot =
            fresh(
                aSession(id = "w1", state = SessionState.Working),
                aSession(id = "q1", state = SessionState.AwaitingInput(null)),
            )

        val modeBar = reduce(snapshot, selection = Selection()).modeBar

        modeBar shouldBe ModeBar(project = null, waiting = 1, working = 1, freshness = UiText("fresh.live"))
    }

    private fun everyState(projectId: ProjectId = project.id): SessionsSnapshot =
        fresh(
            aSession(id = "working", project = projectId, state = SessionState.Working, startedAt = T0),
            aSession(
                id = "input",
                project = projectId,
                state = SessionState.AwaitingInput("q"),
                startedAt = T0.plusSeconds(1),
            ),
            aSession(
                id = "permission",
                project = projectId,
                state = SessionState.AwaitingPermission("Bash"),
                startedAt = T0.plusSeconds(2),
            ),
            aSession(id = "done", project = projectId, state = SessionState.Done, startedAt = T0.plusSeconds(3)),
            aSession(id = "failed", project = projectId, state = SessionState.Failed, startedAt = T0.plusSeconds(4)),
            aSession(id = "stopped", project = projectId, state = SessionState.Stopped, startedAt = T0.plusSeconds(5)),
        )
}
