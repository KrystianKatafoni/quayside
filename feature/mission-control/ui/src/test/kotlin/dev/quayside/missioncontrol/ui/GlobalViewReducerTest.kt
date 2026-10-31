package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
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

/** The screen of a global view from the snapshot, the projects and the selection (FR-S9, AM-407; pure, ENG-C9). */
internal class GlobalViewReducerTest {
    private val project = aProject(displayName = "Quayside")
    private val other = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    private val now: Instant = T0.plusSeconds(600)

    private fun fresh(vararg sessions: Session): SessionsSnapshot =
        SessionsSnapshot(sessions.sortedBy { it.startedAt }, SnapshotStatus.Fresh(T0))

    private fun reduce(
        snapshot: SessionsSnapshot,
        selection: Selection,
        projects: List<Project> = listOf(project, other),
    ): MissionControlUiState = MissionControlReducer.reduce(snapshot, projects, selection, now, ZoneOffset.UTC)

    private fun MissionControlUiState.global(): Screen.Global = screen.shouldBeInstanceOf<Screen.Global>()

    private fun waiting(): SessionsSnapshot =
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
                id = "none-ask",
                project = null,
                state = SessionState.AwaitingInput(null),
                startedAt = T0.plusSeconds(3),
            ),
        )

    @Test
    fun `global view shows the global screen`() {
        val state = reduce(waiting(), Selection(view = GlobalView.WAITING))

        val screen = state.global()
        screen.view shouldBe GlobalView.WAITING
        screen.sessions shouldBe 3
        screen.rows.map { it.id.value } shouldBe listOf("none-ask", "other-perm", "mine-ask")
        screen.details.shouldBeNull()
        screen.banner.shouldBeNull()
        screen.loading shouldBe false
        state.modeBar.project.shouldBeNull()
    }

    @Test
    fun `global view wins over a project that is still selected`() {
        val state = reduce(waiting(), Selection(project = project.id, view = GlobalView.WORKING))

        state.global().rows.map { it.id.value } shouldBe listOf("mine-work")
    }

    @Test
    fun `global view works without projects`() {
        val snapshot =
            fresh(
                aSession(id = "a", project = null, state = SessionState.Failed),
                aSession(id = "b", project = null, state = SessionState.Failed, startedAt = T0.plusSeconds(1)),
            )

        val state = reduce(snapshot, Selection(view = GlobalView.FAILED), projects = emptyList())

        state.global().rows.map { it.id.value } shouldBe listOf("b", "a")
        state.global().sessions shouldBe 2
    }

    @Test
    fun `global rows show the project name or no project`() {
        val snapshot =
            fresh(
                aSession(id = "mine", project = project.id, startedAt = T0),
                aSession(id = "theirs", project = other.id, startedAt = T0.plusSeconds(1)),
                aSession(id = "nowhere", project = null, startedAt = T0.plusSeconds(2)),
                aSession(id = "removed", project = ProjectId("removed"), startedAt = T0.plusSeconds(3)),
            )

        val projects =
            reduce(snapshot, Selection(view = GlobalView.WORKING)).global().rows.associate {
                it.id.value to
                    it.project
            }

        projects shouldBe
            mapOf(
                "mine" to UiText("row.project", listOf("Quayside")),
                "theirs" to UiText("row.project", listOf("Other")),
                "nowhere" to UiText("row.noProject"),
                "removed" to UiText("row.noProject"),
            )
    }

    @Test
    fun `project rows have no project column`() {
        val snapshot = fresh(aSession(id = "mine", project = project.id))

        val page = reduce(snapshot, Selection(project = project.id)).screen.shouldBeInstanceOf<Screen.Project>()

        page.rows.map { it.project } shouldBe listOf(null)
    }

    @Test
    fun `view entries count sessions of every project and mark the shown view`() {
        val snapshot = waiting()
        val all = listOf(GlobalView.WAITING to 3, GlobalView.WORKING to 1, GlobalView.FAILED to 0)

        reduce(snapshot, Selection(view = GlobalView.WORKING)).views shouldBe
            all.map { (view, count) -> ViewEntry(view, count, selected = view == GlobalView.WORKING) }
        // On the project page the counts stay and no entry is marked.
        reduce(snapshot, Selection(project = project.id, filter = SessionFilter.FINISHED)).views shouldBe
            all.map { (view, count) -> ViewEntry(view, count, selected = false) }
        // Without projects the entries still count (the sessions have no project).
        reduce(snapshot, Selection(), projects = emptyList()).views.map { it.count } shouldBe listOf(3, 1, 0)
    }

    @Test
    fun `selected session of any project shows its details`() {
        val selection = Selection(view = GlobalView.WAITING, session = SessionId("other-perm"))

        val screen = reduce(waiting(), selection).global()

        screen.details?.id shouldBe SessionId("other-perm")
        screen.rows.filter { it.selected }.map { it.id.value } shouldBe listOf("other-perm")
    }

    @Test
    fun `selected session leaving the view clears the details`() {
        val snapshot =
            fresh(
                aSession(id = "was-working", project = project.id, state = SessionState.Done),
                aSession(
                    id = "still",
                    project = project.id,
                    state = SessionState.Working,
                    startedAt = T0.plusSeconds(1),
                ),
            )

        val screen = reduce(snapshot, Selection(view = GlobalView.WORKING, session = SessionId("was-working"))).global()

        screen.details.shouldBeNull()
        screen.rows.map { it.id.value } shouldBe listOf("still")
        screen.rows.none { it.selected } shouldBe true
    }

    @Test
    fun `global view shows the banner and loading of the snapshot`() {
        val stale = SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)

        val kept = reduce(SessionsSnapshot(listOf(aSession(id = "kept")), stale), Selection(view = GlobalView.WORKING))
        kept.global().banner shouldBe UiText("fresh.stale.TIMEOUT", listOf("09:00:05"))
        kept.global().rows.map { it.id.value } shouldBe listOf("kept")

        val loading = reduce(SessionsSnapshot.Initial, Selection(view = GlobalView.WORKING))
        loading.global().loading shouldBe true
        loading.global().rows shouldBe emptyList()
    }
}
