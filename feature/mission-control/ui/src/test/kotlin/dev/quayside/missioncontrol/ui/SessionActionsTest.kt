package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Which actions a session offers (plan, table 1) and which session an action hits (decisions 1 and 2). */
internal class SessionActionsTest {
    @ParameterizedTest(name = "{0}, busy={2}")
    @MethodSource("availability")
    fun `actions depend on the state and on a running action`(
        @Suppress("UNUSED_PARAMETER") name: String,
        state: SessionState,
        busy: Boolean,
        expected: Set<SessionAction>,
    ) {
        SessionActions.available(state, busy) shouldBe expected
    }

    private val project = ProjectId("project0001")
    private val working = aSession(id = "working", project = project, state = SessionState.Working)
    private val done = aSession(id = "done", project = project, state = SessionState.Done)
    private val elsewhere = aSession(id = "elsewhere", project = ProjectId("project0002"))
    private val all = listOf(working, done, elsewhere)

    private fun selection(
        session: String? = null,
        filter: SessionFilter = SessionFilter.ALL,
        project: ProjectId? = this.project,
    ) = Selection(project = project, filter = filter, session = session?.let(::SessionId))

    @Test
    fun `the selected session of the project is the target`() {
        SessionActions.target(all, selection("working")) shouldBe working
        SessionActions.target(all, selection("done")) shouldBe done
    }

    @Test
    fun `a session visible under the filter is the target`() {
        SessionActions.target(all, selection("working", SessionFilter.ACTIVE)) shouldBe working
        SessionActions.target(all, selection("done", SessionFilter.FINISHED)) shouldBe done
    }

    @Test
    fun `hidden by the filter gives no target`() {
        SessionActions.target(all, selection("working", SessionFilter.FINISHED)).shouldBeNull()
        SessionActions.target(all, selection("done", SessionFilter.ACTIVE)).shouldBeNull()
    }

    @Test
    fun `a session of another project gives no target`() {
        SessionActions.target(all, selection("elsewhere")).shouldBeNull()
    }

    @Test
    fun `no selection gives no target`() {
        SessionActions.target(all, selection()).shouldBeNull()
        SessionActions.target(all, selection("working", project = null)).shouldBeNull()
    }

    @Test
    fun `a selected session that is gone gives no target`() {
        SessionActions.target(all, selection("gone")).shouldBeNull()
        SessionActions.target(emptyList(), selection("working")).shouldBeNull()
    }

    companion object {
        private val active =
            listOf(
                "working" to SessionState.Working,
                "awaiting input" to SessionState.AwaitingInput("q"),
                "awaiting permission" to SessionState.AwaitingPermission("Bash"),
            )
        private val inactive =
            listOf(
                "done" to SessionState.Done,
                "failed" to SessionState.Failed,
                "stopped" to SessionState.Stopped,
            )

        @JvmStatic
        fun availability(): List<Arguments> =
            active.map { (name, state) -> arguments(name, state, false, setOf(SessionAction.STOP)) } +
                inactive.map { (name, state) ->
                    arguments(name, state, false, setOf(SessionAction.RESPAWN, SessionAction.REMOVE))
                } +
                (active + inactive).map { (name, state) -> arguments(name, state, true, emptySet<SessionAction>()) }
    }
}
