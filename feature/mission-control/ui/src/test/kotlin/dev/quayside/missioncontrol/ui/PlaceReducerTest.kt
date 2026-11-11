package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.ZoneOffset

/**
 * Every place Mission Control computes comes from `Session.placeDir` (FR-P9, AM-314): the page of a subfolder, its
 * counts, the badge of a row, the group overview and the mark in the details. The data has the working directory and
 * the assigned place in different subfolders, so a reducer that still reads `cwd` gives another answer.
 *
 * `carl-by-hand` runs in `bob` and was put in `Carl`; `root-by-hand` runs in `Carl` and was put in the group
 * directory; `anna-auto` runs in `Anna` with no assignment.
 */
internal class PlaceReducerTest {
    private fun session(
        id: String,
        cwd: String,
        assigned: Path?,
        state: SessionState = SessionState.Working,
        index: Long = 0,
    ): Session = groupSession(id, cwd, state, index).copy(assignedDir = assigned)

    private val sessions =
        listOf(
            session("carl-by-hand", "bob/api", OVERVIEW_DIR.resolve("Carl"), index = 0),
            session("root-by-hand", "Carl", OVERVIEW_DIR, SessionState.AwaitingInput("q"), index = 1),
            session("anna-auto", "Anna", null, SessionState.Failed, index = 2),
            session("bob-auto", "bob", null, SessionState.Done, index = 3),
        )

    private fun state(selection: Selection) =
        MissionControlReducer.reduce(
            SessionsSnapshot(sessions, SnapshotStatus.Fresh(T0)),
            listOf(OVERVIEW_GROUP),
            selection,
            T0.plusSeconds(600),
            ZoneOffset.UTC,
        )

    private fun page(selection: Selection): Screen.Project = state(selection).screen as Screen.Project

    private fun onPage(name: String) = page(Selection(project = OVERVIEW_GROUP.id, subfolder = sub(name)))

    @Test
    fun `subfolder page lists the session by its assigned place`() {
        onPage("Carl").rows.map { it.id.value } shouldBe listOf("carl-by-hand")
        onPage("bob").rows.map { it.id.value } shouldBe listOf("bob-auto")
        onPage("Anna").rows.map { it.id.value } shouldBe listOf("anna-auto")
    }

    @Test
    fun `subfolder page counts the session by its assigned place`() {
        onPage("Carl").header.sessions shouldBe 1
        onPage("Carl").stats.counts() shouldBe Counts(working = 1)
        onPage("bob").stats.counts() shouldBe Counts(done = 1)
    }

    @Test
    fun `place badge shows the assigned subfolder`() {
        val places = page(Selection(project = OVERVIEW_GROUP.id)).rows.associate { it.id.value to it.place }

        places shouldBe
            mapOf(
                "carl-by-hand" to UiText("row.place.subfolder", listOf("Carl")),
                "root-by-hand" to UiText("row.place.root"),
                "anna-auto" to UiText("row.place.subfolder", listOf("Anna")),
                "bob-auto" to UiText("row.place.subfolder", listOf("bob")),
            )
    }

    @Test
    fun `group overview counts by the assigned place`() {
        val overview = checkNotNull(page(Selection(project = OVERVIEW_GROUP.id)).overview)

        val byName = overview.rows.associate { it.name.value to it.stats.counts() }

        byName shouldBe
            mapOf(
                "Carl" to Counts(working = 1),
                "bob" to Counts(done = 1),
                "Anna" to Counts(failed = 1),
            )
    }

    @Test
    fun `group overview function counts by the assigned place`() {
        val rows = checkNotNull(GroupOverviews.of(OVERVIEW_GROUP, sessions)).rows

        rows.single { it.name.value == "Carl" }.stats.counts() shouldBe Counts(working = 1)
    }

    @Test
    fun `details show the manual mark only with assignedDir`() {
        fun place(id: String) = page(Selection(project = OVERVIEW_GROUP.id, session = SessionId(id))).details?.place

        place("carl-by-hand") shouldBe UiText("details.place.manual.subfolder", listOf("Carl"))
        place("root-by-hand") shouldBe UiText("details.place.manual.root")
        place("anna-auto") shouldBe null
        place("bob-auto") shouldBe null
    }

    @Test
    fun `details in a global view show the manual mark of the project of the session`() {
        fun place(
            view: GlobalView,
            id: String,
        ) = (state(Selection(view = view, session = SessionId(id))).screen as Screen.Global).details?.place

        place(GlobalView.WORKING, "carl-by-hand") shouldBe UiText("details.place.manual.subfolder", listOf("Carl"))
        place(GlobalView.WAITING, "root-by-hand") shouldBe UiText("details.place.manual.root")
    }

    @Test
    fun `details keep showing the working directory`() {
        val details = page(Selection(project = OVERVIEW_GROUP.id, session = SessionId("carl-by-hand"))).details

        details?.worktree shouldBe OVERVIEW_DIR.resolve("bob/api")
    }
}
