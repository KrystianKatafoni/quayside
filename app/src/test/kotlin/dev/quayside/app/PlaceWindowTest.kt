package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Putting a session in a subfolder by hand, in the whole window on fakes (FR-P9, FR-P7, docs/12 §4, AM-314): the action
 * of the details panel and the row menu, the list of places, the page of the subfolder, the counts and the failure.
 * The group is `plant-puls` with `docs` and `soft` (so `sub.0` is docs and `sub.1` is soft); every session runs in
 * `docs` or in the group directory, and the places the user picks are elsewhere.
 */
@OptIn(ExperimentalTestApi::class)
class PlaceWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val lone = SessionId("lone")
    private val sessions by lazy {
        world.fake(
            world.session("in-docs", "docs", SessionState.Working, 0),
            world.session("rooted", "", SessionState.Done, 1),
            WorldSession(lone, world.other.dir, SessionState.Working, "lone", T0.plusSeconds(2)),
        )
    }
    private val soft get() = WindowTags.subfolderRow("project0001", "soft")
    private val docs get() = WindowTags.subfolderRow("project0001", "docs")

    private fun ComposeUiTest.assign(
        session: String,
        choice: String,
    ) {
        await(WindowTags.missionRow(session)).performClick()
        await(WindowTags.DETAILS_ASSIGN_PLACE).performClick()
        await(WindowTags.PLACE_DIALOG)
        await(WindowTags.placeChoice(choice)).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PLACE_DIALOG))
    }

    private fun ComposeUiTest.markReads(text: String): Boolean =
        onAllNodes(
            hasText(text, substring = true) and
                (hasTestTag(WindowTags.DETAILS_PLACE) or hasAnyAncestor(hasTestTag(WindowTags.DETAILS_PLACE))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `FR-P9 assigning a session to a subfolder moves it to that subfolder page`() =
        runComposeUiTest {
            // Given the group page with a working session that runs in docs
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("in-docs"))
            shows(WindowTags.rowPlace("in-docs"), "docs") shouldBe true

            // When the user puts it in soft from the details panel
            assign("in-docs", "sub.1")

            // Then the badge says soft and the details say it was done by hand
            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { shows(WindowTags.rowPlace("in-docs"), "soft") }
            markReads("Miejsce: soft (ręcznie)") shouldBe true
            sessions.calls shouldBe listOf("assignPlace in-docs")
            // and the page of soft lists it, the page of docs does not
            await(soft).performClick()
            await(WindowTags.missionRow("in-docs"))
            await(docs).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow("in-docs")))
        }

    @Test
    fun `FR-P9 automatic brings the session back to the subfolder of its directory`() =
        runComposeUiTest {
            // Given a session that runs in docs and was put in soft
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            assign("in-docs", "sub.1")
            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { shows(WindowTags.rowPlace("in-docs"), "soft") }

            // When the user picks "Automatycznie"
            await(WindowTags.DETAILS_ASSIGN_PLACE).performClick()
            await(WindowTags.PLACE_DIALOG)
            await(WindowTags.placeChoice("auto")).performClick()

            // Then it is in docs again and the details no longer say "ręcznie"
            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { shows(WindowTags.rowPlace("in-docs"), "docs") }
            onNodeWithTag(WindowTags.DETAILS_PLACE).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 folder główny puts the session in the group directory`() =
        runComposeUiTest {
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()

            assign("in-docs", "root")

            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { shows(WindowTags.rowPlace("in-docs"), "folder główny") }
            markReads("Miejsce: folder główny (ręcznie)") shouldBe true
        }

    @Test
    fun `FR-P9 group overview counts follow the assignment and the group row counts it once`() =
        runComposeUiTest {
            // Given a working session in docs: the overview counts it in docs; the sidebar chips sit on the group row
            // only (the subfolder rows have none, SidebarCountersWindowTest), so that row is the sidebar proof
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            awaitChips(WindowTags.projectRow("project0001"), working = 1)
            awaitChips(WindowTags.overviewRow("docs"), working = 1)

            // When it is put in soft
            assign("in-docs", "sub.1")

            // Then the overview moves the count to soft, and the group row (the whole group) still counts it once
            awaitChips(WindowTags.overviewRow("soft"), working = 1)
            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { counters(WindowTags.overviewRow("docs")).isEmpty() }
            awaitChips(WindowTags.projectRow("project0001"), working = 1)
        }

    @Test
    fun `FR-P9 row menu offers the assignment only in a group`() =
        runComposeUiTest {
            // Given the group page
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("in-docs"))

            // When the menu of a row of the group opens
            await(WindowTags.missionRowMenuButton("in-docs")).performClick()

            // Then the assignment is offered, and choosing it opens the list with the current line selected
            await(WindowTags.ROW_MENU_ASSIGN_PLACE).performClick()
            await(WindowTags.PLACE_DIALOG)
            await(WindowTags.PLACE_CANCEL).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.PLACE_DIALOG))

            // And a project without subfolders offers no assignment
            await(WindowTags.projectRow("project0002")).performClick()
            await(WindowTags.missionRowMenuButton(lone.value)).performClick()
            await(WindowTags.menuItem("preview"))
            onNodeWithTag(WindowTags.ROW_MENU_ASSIGN_PLACE).assertDoesNotExist()
            onNodeWithTag(WindowTags.DETAILS_ASSIGN_PLACE).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 failed assignment shows the reason and keeps the place`() =
        runComposeUiTest {
            // Given a save that fails
            sessions.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()

            // When the user puts a session in soft
            assign("in-docs", "sub.1")

            // Then a fixed reason shows, without a name, and the session stays where it was
            await(WindowTags.DETAILS_FAILURE)
            shows(WindowTags.DETAILS_FAILURE, "in-docs") shouldBe false
            shows(WindowTags.DETAILS_FAILURE, "soft") shouldBe false
            shows(WindowTags.rowPlace("in-docs"), "docs") shouldBe true
            onNodeWithTag(WindowTags.DETAILS_PLACE).assertDoesNotExist()
        }
}
