package dev.quayside.projects.ui

import dev.quayside.core.testing.T0
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Counters of the subfolder rows (FR-P7, AM-306): sessions of the group project by the directory they run in. */
class SessionsViewSubfolderTest {
    private val plant =
        aProject(id = "plant", dir = Path.of("/p/plant-puls"), subfolders = subfolderNames("docs", "soft"))
    private val inner = aProject(id = "inner", dir = Path.of("/p/plant-puls/docs"), displayName = "inner")
    private val soft = ProjectFocus(plant.id, SubfolderName("soft"))
    private val docs = ProjectFocus(plant.id, SubfolderName("docs"))
    private val fresh = SnapshotStatus.Fresh(T0)

    private fun session(
        id: String,
        cwd: String,
        state: SessionState = SessionState.Working,
        project: dev.quayside.core.domain.ProjectId = plant.id,
    ) = aSession(id = id, project = project, state = state, cwd = Path.of(cwd))

    private fun view(vararg sessions: dev.quayside.sessions.api.Session) =
        SessionsView.of(listOf(plant, inner), SessionsSnapshot(sessions.toList(), fresh))

    @Test
    fun `counts sessions per subfolder`() {
        val view =
            view(
                session("a", "/p/plant-puls/soft/api"),
                session("b", "/p/plant-puls/soft", SessionState.AwaitingInput("q")),
                session("c", "/p/plant-puls/docs/x"),
                session("d", "/p/plant-puls/soft", SessionState.Done),
            )

        view.subfolders[soft] shouldBe SessionCounts(working = 1, waiting = 1)
        view.subfolders[docs] shouldBe SessionCounts(working = 1, waiting = 0)
    }

    @Test
    fun `the project row still counts every session of the project`() {
        val view =
            view(
                session("a", "/p/plant-puls/soft/api"),
                session("b", "/p/plant-puls", SessionState.AwaitingInput(null)),
                session("c", "/p/plant-puls/marketing"),
            )

        view.counts[plant.id] shouldBe SessionCounts(working = 2, waiting = 1)
    }

    @Test
    fun `session in the project directory counts only for the project row`() {
        val view = view(session("root", "/p/plant-puls"))

        view.counts[plant.id] shouldBe SessionCounts(working = 1, waiting = 0)
        view.subfolders shouldBe emptyMap()
    }

    @Test
    fun `session in a subdirectory that is not a subfolder counts only for the project row`() {
        val view = view(session("loose", "/p/plant-puls/marketing/site"), session("sibling", "/p/plant-puls/software"))

        view.counts[plant.id] shouldBe SessionCounts(working = 2, waiting = 0)
        view.subfolders shouldBe emptyMap()
    }

    @Test
    fun `session of a nested project is not counted in the subfolder`() {
        // docs is a subfolder of plant and also a project of its own: the session belongs to the deeper project
        val view = view(session("deep", "/p/plant-puls/docs/guide", project = inner.id))

        view.subfolders shouldBe emptyMap()
        view.counts[plant.id] shouldBe null
        view.counts[inner.id] shouldBe SessionCounts(working = 1, waiting = 0)
    }

    @Test
    fun `subfolders without active sessions have no entry`() {
        val view = view(session("ended", "/p/plant-puls/soft", SessionState.Done))

        view.subfolders shouldBe emptyMap()
    }

    @Test
    fun `stale snapshot keeps the subfolder counts but is not fresh`() {
        val stale = SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)

        val view = SessionsView.of(listOf(plant), SessionsSnapshot(listOf(session("a", "/p/plant-puls/soft")), stale))

        view.fresh shouldBe false
        view.subfolders shouldBe mapOf(soft to SessionCounts(working = 1, waiting = 0))
    }

    @Test
    fun `loading snapshot gives no subfolder counts`() {
        val view =
            SessionsView.of(
                listOf(plant),
                SessionsSnapshot(listOf(session("a", "/p/plant-puls/soft")), SnapshotStatus.Loading),
            )

        view shouldBe SessionsView.Unknown
        view.subfolders shouldBe emptyMap()
    }

    @Test
    fun `project without subfolders has no subfolder entries`() {
        val plain = plant.copy(subfolders = emptyList())

        SessionsView
            .of(
                listOf(plain),
                SessionsSnapshot(listOf(session("a", "/p/plant-puls/soft")), fresh),
            ).subfolders shouldBe
            emptyMap()
    }
}
