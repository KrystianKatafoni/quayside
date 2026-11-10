package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** [SessionsView.statuses] without the keys that hold only zeros ("missing key = all zero"). */
internal fun SessionsView.nonZero(): Map<ProjectFocus, StatusCounts> = statuses.filterValues { it != StatusCounts() }

/** The four counters of a row; a missing key is all zero. */
internal fun SessionsView.at(focus: ProjectFocus): StatusCounts = statuses[focus] ?: StatusCounts()

/**
 * The four counters of every sidebar row (FR-P6, FR-P7, AM-310): the main row of a project sums the whole group
 * (`Session.project == id`, like the session list), a subfolder row only the sessions whose directory is in it.
 */
internal class SessionsViewStatusesTest {
    private val plant =
        aProject(id = "plant", dir = Path.of("/p/plant-puls"), subfolders = subfolderNames("docs", "soft"))
    private val inner = aProject(id = "inner", dir = Path.of("/p/plant-puls/docs"), displayName = "inner")
    private val lone = aProject(id = "lone", dir = Path.of("/p/lone"), displayName = "lone")
    private val main = ProjectFocus(plant.id)
    private val soft = ProjectFocus(plant.id, SubfolderName("soft"))
    private val docs = ProjectFocus(plant.id, SubfolderName("docs"))
    private val fresh = SnapshotStatus.Fresh(T0)

    private fun session(
        id: String,
        cwd: String,
        state: SessionState = SessionState.Working,
        project: ProjectId? = plant.id,
    ) = aSession(id = id, project = project, state = state, cwd = Path.of(cwd))

    private fun view(
        vararg sessions: Session,
        projects: List<Project> = listOf(plant, inner, lone),
    ) = SessionsView.of(projects, SessionsSnapshot(sessions.toList(), fresh))

    @Test
    fun `main row sums the whole group with its subfolders`() {
        val view =
            view(
                session("root", "/p/plant-puls"),
                session("api", "/p/plant-puls/soft/api", SessionState.AwaitingInput("q")),
                session("guide", "/p/plant-puls/docs", SessionState.Failed),
                session("site", "/p/plant-puls/marketing/site", SessionState.Done),
                session("old", "/p/plant-puls/marketing", SessionState.Done),
            )

        view.at(main) shouldBe StatusCounts(waiting = 1, working = 1, failed = 1)
    }

    @Test
    fun `subfolder row counts only its own sessions`() {
        val view =
            view(
                session("a", "/p/plant-puls/soft/api"),
                session("b", "/p/plant-puls/soft"),
                session("c", "/p/plant-puls/soft", SessionState.AwaitingPermission("Bash")),
                session("d", "/p/plant-puls/docs/x"),
                session("e", "/p/plant-puls"),
            )

        view.at(soft) shouldBe StatusCounts(waiting = 1, working = 2)
        view.at(docs) shouldBe StatusCounts(working = 1)
    }

    @Test
    fun `failed is counted per subfolder and done is not counted`() {
        val view =
            view(
                session("a", "/p/plant-puls/soft", SessionState.Done),
                session("b", "/p/plant-puls/soft/api", SessionState.Done),
                session("c", "/p/plant-puls/soft", SessionState.Failed),
                session("d", "/p/plant-puls/docs", SessionState.Done),
            )

        view.at(soft) shouldBe StatusCounts(failed = 1)
        view.at(docs) shouldBe StatusCounts()
        view.nonZero().keys shouldBe setOf(main, soft)
    }

    @Test
    fun `stopped sessions are not counted`() {
        val view =
            view(
                session("a", "/p/plant-puls", SessionState.Stopped),
                session("b", "/p/plant-puls/soft", SessionState.Stopped),
                session("c", "/p/lone", SessionState.Stopped, project = lone.id),
            )

        view.nonZero() shouldBe emptyMap()
    }

    @Test
    fun `a session in the root or an unchecked subdirectory counts only on the main row`() {
        val view = view(session("root", "/p/plant-puls"), session("loose", "/p/plant-puls/marketing/site"))

        view.at(main) shouldBe StatusCounts(working = 2)
        view.nonZero().keys shouldBe setOf(main)
    }

    @Test
    fun `soft does not count software`() {
        val view = view(session("sibling", "/p/plant-puls/software"), session("own", "/p/plant-puls/soft/api"))

        view.at(soft) shouldBe StatusCounts(working = 1)
        view.at(main) shouldBe StatusCounts(working = 2)
    }

    @Test
    fun `a nested project keeps its sessions`() {
        // docs is a subfolder of plant and also a project of its own: the session belongs to the deeper project
        val view = view(session("deep", "/p/plant-puls/docs/guide", project = inner.id))

        view.nonZero() shouldBe mapOf(ProjectFocus(inner.id) to StatusCounts(working = 1))
        view.at(docs) shouldBe StatusCounts()
        view.at(main) shouldBe StatusCounts()
    }

    @Test
    fun `sessions of no listed project count nowhere`() {
        val view =
            view(
                session("stray", "/p/plant-puls/soft", project = null),
                session("unlisted", "/p/plant-puls", project = ProjectId("not-listed")),
            )

        view.nonZero() shouldBe emptyMap()
    }

    @Test
    fun `project without subfolders has only its main entry`() {
        val view =
            view(
                session("a", "/p/lone", project = lone.id),
                session("b", "/p/lone/deep/er", SessionState.Done, project = lone.id),
                session("c", "/p/lone/soft", SessionState.Failed, project = lone.id),
            )

        view.nonZero() shouldBe mapOf(ProjectFocus(lone.id) to StatusCounts(working = 1, failed = 1))
    }

    @Test
    fun `stale keeps the statuses`() {
        val stale = SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)
        val sessions = listOf(session("a", "/p/plant-puls/soft"), session("b", "/p/plant-puls", SessionState.Done))

        val view = SessionsView.of(listOf(plant), SessionsSnapshot(sessions, stale))

        view.fresh shouldBe false
        view.at(soft) shouldBe StatusCounts(working = 1)
        view.at(main) shouldBe StatusCounts(working = 1)
    }

    @Test
    fun `loading gives none`() {
        val loading = SessionsSnapshot(listOf(session("a", "/p/plant-puls/soft")), SnapshotStatus.Loading)

        val view = SessionsView.of(listOf(plant), loading)

        view shouldBe SessionsView.Unknown
        view.nonZero() shouldBe emptyMap()
        view.at(main) shouldBe StatusCounts()
    }

    @Test
    fun `counts for removal stay active only`() {
        val view =
            view(
                session("work", "/p/plant-puls/soft"),
                session("ask", "/p/plant-puls", SessionState.AwaitingInput(null)),
                session("fin", "/p/plant-puls/docs", SessionState.Done),
                session("bad", "/p/plant-puls/docs", SessionState.Failed),
                session("off", "/p/lone", SessionState.Stopped, project = lone.id),
                session("fin2", "/p/lone", SessionState.Done, project = lone.id),
            )

        view.counts[plant.id] shouldBe SessionCounts(working = 1, waiting = 1)
        // only done, failed or stopped sessions: nothing active, so no entry (the remove dialog stays quiet)
        view.counts[lone.id] shouldBe null
    }
}
