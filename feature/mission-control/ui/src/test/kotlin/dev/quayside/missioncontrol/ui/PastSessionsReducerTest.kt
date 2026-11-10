package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.ProjectHistory
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.time.ZoneOffset

/** Disk sessions on the screen of Mission Control (FR-P9, FR-S9): which filters and views show them, the older row. */
internal class PastSessionsReducerTest {
    private val project = aProject(id = "project0001", displayName = "Quayside", subfolders = subfolderNames("soft"))
    private val now: Instant = T0.plusSeconds(600)
    private val onDisk =
        pastSession(id = "00000000-0000-4000-8000-000000000001", project = project.id, name = "Old chat")
    private val running =
        aSession(id = "run00001", project = project.id, name = "Live agent", startedAt = T0.plusSeconds(10))
    private val stopped =
        aSession(id = "stop0001", project = project.id, name = "Stopped agent", state = SessionState.Stopped)

    private fun snapshot(
        vararg sessions: Session,
        history: Map<ProjectId, ProjectHistory> = emptyMap(),
    ) = SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(T0), history)

    private fun reduce(
        snapshot: SessionsSnapshot,
        selection: Selection = Selection(project = project.id),
        projects: List<Project> = listOf(project),
    ): MissionControlUiState = MissionControlReducer.reduce(snapshot, projects, selection, now, ZoneOffset.UTC)

    private fun MissionControlUiState.page(): Screen.Project = screen.shouldBeInstanceOf<Screen.Project>()

    private fun Screen.Project.ids(): List<String> = rows.map { it.id.value }

    @Test
    fun `FR-S9 disk session is in all and finished but not active`() {
        val all = snapshot(onDisk, running)

        reduce(all, Selection(project = project.id, filter = SessionFilter.ALL)).page().ids() shouldBe
            listOf(running.id.value, onDisk.id.value)
        reduce(all, Selection(project = project.id, filter = SessionFilter.FINISHED)).page().ids() shouldBe
            listOf(onDisk.id.value)
        reduce(all, Selection(project = project.id, filter = SessionFilter.ACTIVE)).page().ids() shouldBe
            listOf(running.id.value)
    }

    @ParameterizedTest
    @EnumSource(GlobalView::class)
    fun `FR-S9 disk session is in no global view`(view: GlobalView) {
        val state = reduce(snapshot(onDisk), Selection(view = view))

        state.screen.shouldBeInstanceOf<Screen.Global>().rows shouldBe emptyList()
        state.views.map { it.count } shouldBe listOf(0, 0, 0, 0)
    }

    @Test
    fun `the mode bar counts only supervisor sessions`() {
        val state = reduce(snapshot(onDisk, onDisk.copy(id = SessionId("other"))))

        state.modeBar.waiting shouldBe 0
        state.modeBar.working shouldBe 0
    }

    @Test
    fun `stats cards do not count disk sessions`() {
        val page = reduce(snapshot(onDisk, onDisk.copy(id = SessionId("other")))).page()

        page.stats shouldBe SessionStats()
    }

    @Test
    fun `a disk row is marked from disk and a supervisor row is not, also when stopped`() {
        val rows = reduce(snapshot(onDisk, stopped)).page().rows.associate { it.id.value to it.fromDisk }

        rows shouldBe mapOf(onDisk.id.value to true, stopped.id.value to false)
    }

    @Test
    fun `a disk row looks stopped and says so in the last event column`() {
        val row = reduce(snapshot(onDisk)).page().rows.single()

        row.glyph.key shouldBe "state.stopped"
        row.lastEvent shouldBe LastEvent.of(onDisk.state)
        row.name shouldBe "Old chat"
    }

    @Test
    fun `the older row count comes from the history of the shown project`() {
        val history =
            mapOf(
                project.id to ProjectHistory(shown = 3, older = 14),
                ProjectId("elsewhere") to ProjectHistory(1, 9),
            )

        reduce(snapshot(onDisk, history = history)).page().older shouldBe 14
    }

    @Test
    fun `no older row without older sessions or without a history key`() {
        reduce(snapshot(onDisk, history = mapOf(project.id to ProjectHistory(1, 0)))).page().older shouldBe 0
        reduce(snapshot(onDisk)).page().older shouldBe 0
    }

    @Test
    fun `older row only on the group page, not on the page of a subfolder`() {
        val history = mapOf(project.id to ProjectHistory(shown = 3, older = 14))
        val group = Selection(project = project.id)
        val soft = Selection(project = project.id, subfolder = SubfolderName("soft"))

        reduce(snapshot(onDisk, history = history), group).page().older shouldBe 14
        reduce(snapshot(onDisk, history = history), soft).page().older shouldBe 0
    }

    @Test
    fun `a project in a nested directory has its own history`() {
        val nested = aProject(id = "nested", dir = A_PROJECT_DIR.resolve("tools"), displayName = "Tools")
        val history = mapOf(nested.id to ProjectHistory(1, 5), project.id to ProjectHistory(1, 2))

        reduce(
            snapshot(history = history),
            Selection(project = nested.id),
            listOf(project, nested),
        ).page().older shouldBe
            5
    }
}
