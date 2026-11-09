package dev.quayside.missioncontrol.ui

import dev.quayside.core.testing.T0
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * The place badge in the rows of the reducer (FR-P7, FR-S9, AM-310): only the group page (no subfolder selected) of a
 * project that has subfolders says where each session runs. The group is `plant-puls` with `bob`, `Carl`, `Anna`.
 */
internal class GroupPlaceReducerTest {
    private val root = UiText("row.place.root")

    private fun at(name: String) = UiText("row.place.subfolder", listOf(name))

    private val sessions =
        listOf(
            groupSession("in-carl", "Carl", SessionState.Working, index = 0),
            groupSession("in-bob", "bob/api", SessionState.AwaitingInput("q"), index = 1),
            groupSession("rooted", "", SessionState.Working, index = 2),
            groupSession("loose", "marketing/site", SessionState.Working, index = 3),
            groupSession("sibling", "bobx", SessionState.Working, index = 4),
            groupSession("fin-anna", "Anna", SessionState.Done, index = 5),
            groupSession("bad-root", "", SessionState.Failed, index = 6),
        )

    private fun rows(
        selection: Selection = Selection(project = OVERVIEW_GROUP.id),
        projects: List<Project> = listOf(OVERVIEW_GROUP),
    ): Map<String, UiText?> {
        val snapshot = SessionsSnapshot(sessions, SnapshotStatus.Fresh(T0))
        val state = MissionControlReducer.reduce(snapshot, projects, selection, T0.plusSeconds(600), ZoneOffset.UTC)
        val screen = state.screen
        return when (screen) {
            is Screen.Project -> screen.rows.associate { it.id.value to it.place }
            is Screen.Global -> screen.rows.associate { it.id.value to it.place }
            else -> error("no rows on $screen")
        }
    }

    @Test
    fun `group page rows say where each session runs`() {
        rows() shouldBe
            mapOf(
                "in-carl" to at("Carl"),
                "in-bob" to at("bob"),
                "rooted" to root,
                "loose" to root,
                "sibling" to root,
                "fin-anna" to at("Anna"),
                "bad-root" to root,
            )
    }

    @Test
    fun `subfolder page rows have no place`() {
        val page = rows(Selection(project = OVERVIEW_GROUP.id, subfolder = sub("Carl")))

        page shouldBe mapOf("in-carl" to null)
    }

    @Test
    fun `project without subfolders has no place`() {
        val plain = aProject(id = OVERVIEW_GROUP.id.value, dir = OVERVIEW_DIR, displayName = "plant-puls")

        val page = rows(Selection(project = plain.id), listOf(plain))

        page.size shouldBe sessions.size
        page.values.toSet() shouldBe setOf<UiText?>(null)
    }

    @Test
    fun `global view rows have no place`() {
        val page = rows(Selection(view = GlobalView.WORKING), listOf(OVERVIEW_GROUP))

        page.keys shouldBe setOf("in-carl", "rooted", "loose", "sibling")
        page.values.toSet() shouldBe setOf<UiText?>(null)
    }

    @Test
    fun `place stays under the active and ended filters`() {
        val active = rows(Selection(project = OVERVIEW_GROUP.id, filter = SessionFilter.ACTIVE))
        val ended = rows(Selection(project = OVERVIEW_GROUP.id, filter = SessionFilter.FINISHED))

        active shouldBe
            mapOf(
                "in-carl" to at("Carl"),
                "in-bob" to at("bob"),
                "rooted" to root,
                "loose" to root,
                "sibling" to root,
            )
        ended shouldBe mapOf("fin-anna" to at("Anna"), "bad-root" to root)
    }
}
