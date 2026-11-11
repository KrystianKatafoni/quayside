package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

/** What a disk session offers in the details panel (FR-P9, AM-313): "Wznów" and "Usuń", nothing else. */
internal class PastSessionActionsTest {
    private val project = aProject(id = "project0001", displayName = "Quayside")
    private val projects = listOf(project)
    private val disk = pastSession(id = "00000000-0000-4000-8000-000000000001", project = project.id)
    private val stopped = aSession(id = "stop0001", project = project.id, state = SessionState.Stopped)
    private val snapshot = SessionsSnapshot(listOf(disk, stopped), SnapshotStatus.Fresh(T0))

    private fun details(
        session: SessionId,
        acting: ActionUi = ActionUi(),
    ): SessionDetails {
        val selection = Selection(project = project.id, session = session)
        val state =
            MissionControlReducer
                .reduce(snapshot, projects, selection, Instant.EPOCH, ZoneOffset.UTC)
                .withActions(snapshot, selection, acting, projects)
        return state.screen
            .shouldBeInstanceOf<Screen.Project>()
            .details
            .shouldBeInstanceOf<SessionDetails>()
    }

    @Test
    fun `disk session offers resume and remove`() {
        details(disk.id).actions shouldBe setOf(SessionAction.RESUME, SessionAction.REMOVE)
    }

    @Test
    fun `a stopped supervisor session still offers respawn and remove`() {
        details(stopped.id).actions shouldBe setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
    }

    @Test
    fun `disk session cannot attach or reply`() {
        val shown = details(disk.id)

        shown.canAttach shouldBe false
        shown.canReply shouldBe false
        SessionAttach.targetOf(disk, projects, dialogOpen = false).shouldBeNull()
    }

    @Test
    fun `a stopped supervisor session can still attach`() {
        details(stopped.id).canAttach shouldBe true
    }

    @Test
    fun `disk session offers nothing while an action runs`() {
        details(disk.id, ActionUi(busy = disk.id to SessionAction.REMOVE)).actions shouldBe emptySet()
    }
}
