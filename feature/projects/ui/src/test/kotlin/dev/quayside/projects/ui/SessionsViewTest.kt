package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Pure projection of a sessions snapshot onto the listed projects (FR-P6; AM-305 plan, decision 1). */
class SessionsViewTest {
    private val outer = aProject(id = "outer", dir = Path.of("/p/outer"), displayName = "outer")
    private val inner = aProject(id = "inner", dir = Path.of("/p/outer/inner"), displayName = "inner")
    private val lone = aProject(id = "lone", dir = Path.of("/p/lone"), displayName = "lone")
    private val fresh = SnapshotStatus.Fresh(T0)

    private fun session(
        id: String,
        project: ProjectId?,
        state: SessionState = SessionState.Working,
    ) = aSession(id = id, project = project, state = state)

    @Test
    fun `loading snapshot gives no counts`() {
        val snapshot = SessionsSnapshot(listOf(session("a", outer.id)), SnapshotStatus.Loading)

        SessionsView.of(listOf(outer), snapshot) shouldBe SessionsView.Unknown
        SessionsView.of(listOf(outer), SessionsSnapshot.Initial) shouldBe SessionsView.Unknown
        SessionsView.Unknown.counts shouldBe emptyMap()
        SessionsView.Unknown.fresh shouldBe false
    }

    @Test
    fun `stale snapshot keeps last counts but is not fresh`() {
        val stale = SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)
        val snapshot = SessionsSnapshot(listOf(session("a", outer.id)), stale)

        SessionsView.of(listOf(outer), snapshot) shouldBe
            SessionsView(mapOf(outer.id to SessionCounts(working = 1, waiting = 0)), fresh = false)
    }

    @Test
    fun `stale snapshot without any listing is not fresh and empty`() {
        val stale = SnapshotStatus.Stale(lastFreshAt = null, problem = RuntimeProblem.NOT_INSTALLED)

        SessionsView.of(listOf(outer), SessionsSnapshot(emptyList(), stale)) shouldBe
            SessionsView(emptyMap(), fresh = false)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    fun `counts only active sessions of each project`(
        @Suppress("UNUSED_PARAMETER") name: String,
        states: List<SessionState>,
        expected: SessionCounts?,
    ) {
        val sessions = states.mapIndexed { index, state -> session("s$index", outer.id, state) }
        val view = SessionsView.of(listOf(outer, lone), SessionsSnapshot(sessions, fresh))

        view.fresh shouldBe true
        view.counts[outer.id] shouldBe expected
        view.counts[lone.id] shouldBe null
    }

    @Test
    fun `nested project sessions are not counted twice`() {
        val snapshot =
            SessionsSnapshot(
                listOf(
                    session("in-outer", outer.id),
                    session("in-inner", inner.id, SessionState.AwaitingInput(null)),
                ),
                fresh,
            )

        SessionsView.of(listOf(outer, inner), snapshot) shouldBe
            SessionsView(
                mapOf(outer.id to SessionCounts(1, 0), inner.id to SessionCounts(0, 1)),
                fresh = true,
            )
    }

    @Test
    fun `projects without active sessions have no entry`() {
        val snapshot =
            SessionsSnapshot(
                listOf(
                    session("ended", outer.id, SessionState.Done),
                    session("stray", project = null),
                    session("unlisted", ProjectId("not-listed")),
                ),
                fresh,
            )

        SessionsView.of(listOf(outer, lone), snapshot) shouldBe SessionsView(emptyMap(), fresh = true)
    }

    companion object {
        @JvmStatic
        fun states(): List<Arguments> =
            listOf(
                arguments("working", listOf(SessionState.Working), SessionCounts(1, 0)),
                arguments("waiting for input", listOf(SessionState.AwaitingInput("q")), SessionCounts(0, 1)),
                arguments(
                    "waiting for permission",
                    listOf(SessionState.AwaitingPermission("Bash")),
                    SessionCounts(0, 1),
                ),
                arguments(
                    "mixed",
                    listOf(
                        SessionState.Working,
                        SessionState.Working,
                        SessionState.AwaitingInput(null),
                        SessionState.AwaitingPermission(null),
                        SessionState.Done,
                        SessionState.Failed,
                        SessionState.Stopped,
                    ),
                    SessionCounts(working = 2, waiting = 2),
                ),
                arguments("done", listOf(SessionState.Done), null),
                arguments("failed", listOf(SessionState.Failed), null),
                arguments("stopped", listOf(SessionState.Stopped), null),
                arguments("none", emptyList<SessionState>(), null),
            )
    }
}
