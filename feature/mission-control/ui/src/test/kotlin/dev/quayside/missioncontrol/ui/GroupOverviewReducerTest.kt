package dev.quayside.missioncontrol.ui

import dev.quayside.core.testing.T0
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

/** The overview on the group page of the reducer (FR-P8, FR-S9, AM-307, AM-310: no root row). */
internal class GroupOverviewReducerTest {
    private val now: Instant = T0.plusSeconds(600)

    private val sessions =
        listOf(
            groupSession("ask", "Carl", SessionState.AwaitingInput("q"), index = 0),
            groupSession("boom", "bob/api", SessionState.Failed, index = 1),
            groupSession("busy", "Anna", SessionState.Working, index = 2),
            groupSession("fin", "Anna", SessionState.Done, index = 3),
            groupSession("loose", "", SessionState.Working, index = 4),
        )

    private fun fresh(vararg list: Session) = SessionsSnapshot(list.toList(), SnapshotStatus.Fresh(T0))

    private fun page(
        snapshot: SessionsSnapshot = fresh(*sessions.toTypedArray()),
        selection: Selection = Selection(project = OVERVIEW_GROUP.id),
        projects: List<Project> = listOf(OVERVIEW_GROUP),
    ): Screen.Project =
        MissionControlReducer
            .reduce(snapshot, projects, selection, now, ZoneOffset.UTC)
            .screen
            .shouldBeInstanceOf<Screen.Project>()

    private fun Screen.Project.shown(): List<Pair<String, Counts>> =
        overview.shouldNotBeNull().rows.map { it.name.value to it.stats.counts() }

    @Test
    fun `group page has the overview`() {
        val screen = page()

        screen.shown() shouldBe
            listOf(
                "Carl" to Counts(waiting = 1),
                "bob" to Counts(failed = 1),
                "Anna" to Counts(working = 1, done = 1),
            )
        screen.overview.shouldNotBeNull().project shouldBe OVERVIEW_GROUP.id
    }

    @Test
    fun `subfolder page has no overview`() {
        page(selection = Selection(project = OVERVIEW_GROUP.id, subfolder = sub("Anna"))).overview.shouldBeNull()
    }

    @Test
    fun `project without subfolders has no overview`() {
        val plain = aProject(id = "plain", dir = OVERVIEW_DIR, displayName = "plain")

        page(projects = listOf(plain), selection = Selection(project = plain.id)).overview.shouldBeNull()
    }

    @Test
    fun `overview is absent while loading`() {
        val screen = page(snapshot = SessionsSnapshot.Initial)

        screen.loading shouldBe true
        screen.overview.shouldBeNull()
    }

    @Test
    fun `stale snapshot keeps the overview and the banner`() {
        val stale = SnapshotStatus.Stale(T0.plusSeconds(5), RuntimeProblem.TIMEOUT)

        val screen = page(snapshot = SessionsSnapshot(sessions, stale))

        screen.banner shouldBe UiText("fresh.stale.TIMEOUT", listOf("09:00:05"))
        screen.loading shouldBe false
        screen.shown().first() shouldBe ("Carl" to Counts(waiting = 1))
    }

    @Test
    fun `overview ignores the filter tabs`() {
        val shown = SessionFilter.entries.map { page(selection = Selection(OVERVIEW_GROUP.id, filter = it)).shown() }

        // The failed and the done sessions are not "active", yet every tab counts them the same.
        shown.distinct() shouldBe listOf(page().shown())
        page(selection = Selection(OVERVIEW_GROUP.id, filter = SessionFilter.ACTIVE)).shown() shouldBe
            listOf(
                "Carl" to Counts(waiting = 1),
                "bob" to Counts(failed = 1),
                "Anna" to Counts(working = 1, done = 1),
            )
    }
}
