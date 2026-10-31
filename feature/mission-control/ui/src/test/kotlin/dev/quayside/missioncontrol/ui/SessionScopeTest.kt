package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Which sessions the list, j and k and the actions see (AM-407 plan, decisions 3 and 4). */
internal class SessionScopeTest {
    private val mine = ProjectId("mine")
    private val other = ProjectId("other")

    private val sessions: List<Session> =
        listOf(
            aSession(id = "mine-working", project = mine, state = SessionState.Working, startedAt = T0),
            aSession(id = "mine-done", project = mine, state = SessionState.Done, startedAt = T0.plusSeconds(1)),
            aSession(
                id = "other-working",
                project = other,
                state = SessionState.Working,
                startedAt = T0.plusSeconds(2),
            ),
            aSession(
                id = "other-asking",
                project = other,
                state = SessionState.AwaitingInput("q"),
                startedAt = T0.plusSeconds(3),
            ),
            aSession(id = "none-working", project = null, state = SessionState.Working, startedAt = T0.plusSeconds(4)),
            aSession(id = "none-failed", project = null, state = SessionState.Failed, startedAt = T0.plusSeconds(5)),
            aSession(
                id = "mine-permission",
                project = mine,
                state = SessionState.AwaitingPermission("Bash"),
                startedAt = T0.plusSeconds(6),
            ),
        )

    private fun visible(selection: Selection): List<String> =
        SessionScope.visible(sessions, selection).map { it.id.value }

    @Test
    fun `project page shows sessions of the project under the filter`() {
        visible(Selection(project = mine)) shouldBe listOf("mine-permission", "mine-done", "mine-working")
        visible(Selection(project = mine, filter = SessionFilter.ACTIVE)) shouldBe
            listOf("mine-permission", "mine-working")
        visible(Selection(project = mine, filter = SessionFilter.FINISHED)) shouldBe listOf("mine-done")
    }

    @Test
    fun `project page without a project shows nothing`() {
        visible(Selection()) shouldBe emptyList()
    }

    @Test
    fun `global view shows matching sessions of every project`() {
        visible(Selection(view = GlobalView.WORKING)) shouldBe
            listOf("none-working", "other-working", "mine-working")
        visible(Selection(view = GlobalView.WAITING)) shouldBe
            listOf("mine-permission", "other-asking")
    }

    @Test
    fun `global view includes sessions without a project`() {
        visible(Selection(view = GlobalView.FAILED)) shouldBe listOf("none-failed")
    }

    @Test
    fun `global view ignores the filter and a project that is still selected`() {
        visible(Selection(view = GlobalView.WORKING, filter = SessionFilter.FINISHED)) shouldBe
            listOf("none-working", "other-working", "mine-working")
        visible(Selection(project = other, view = GlobalView.WORKING, filter = SessionFilter.ACTIVE)) shouldBe
            listOf("none-working", "other-working", "mine-working")
    }

    @Test
    fun `newest session is on top`() {
        val unsorted = listOf(sessions[3], sessions[0], sessions[6], sessions[2])

        SessionScope
            .visible(unsorted, Selection(view = GlobalView.WORKING))
            .map { it.id } shouldBe listOf(SessionId("other-working"), SessionId("mine-working"))
        SessionScope
            .visible(unsorted, Selection(project = mine))
            .map { it.id } shouldBe listOf(SessionId("mine-permission"), SessionId("mine-working"))
    }
}
