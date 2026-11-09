package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The place badge of the session list in the whole window on fakes (FR-P7, FR-S9, docs/12 §4, AM-310): the group page
 * says where each session runs, the page of a subfolder does not. Sessions are named after where they run.
 */
@OptIn(ExperimentalTestApi::class)
class SessionPlaceWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val sessions by lazy {
        world.fake(
            world.session("rooted", "", SessionState.Working, 0),
            world.session("in-soft", "soft/api", SessionState.Working, 1),
            world.session("in-docs", "docs", SessionState.Done, 2),
            world.session("loose", "marketing", SessionState.Failed, 3),
        )
    }
    private val soft get() = WindowTags.subfolderRow("project0001", "soft")

    @Test
    fun `FR-P7 group page shows where each session runs`() =
        runComposeUiTest {
            // Given the page of the group
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("rooted"))

            // Then each row says where its session runs, in a badge with text
            shows(WindowTags.rowPlace("in-soft"), "soft") shouldBe true
            reads(WindowTags.rowPlace("in-soft"), "folder: soft") shouldBe true
            shows(WindowTags.rowPlace("in-docs"), "docs") shouldBe true
            // and the root, an unchecked subdirectory and a finished or failed session say "folder główny" too
            listOf("rooted", "loose").forEach {
                shows(WindowTags.rowPlace(it), "folder główny") shouldBe true
                reads(WindowTags.rowPlace(it), "folder główny") shouldBe true
            }
        }

    @Test
    fun `FR-P7 subfolder page shows no place badges`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft", reached from the sidebar
            showWindow(world.projects, sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("rooted"))
            tagged("missionControl.rowPlace.") shouldBe 4
            await(soft).performClick()
            await(WindowTags.missionRow("in-soft"))
            waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { tagged("missionControl.row.") == 1 }

            // Then no row has a badge: the page itself names the subfolder
            tagged("missionControl.rowPlace.") shouldBe 0

            // And the group page has them again
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("rooted"))
            tagged("missionControl.rowPlace.") shouldBe 4
        }

    @Test
    fun `FR-P7 project without subfolders shows no place badges`() =
        runComposeUiTest {
            // Given a plain project with a session of its own
            val elsewhere = WorldSession(SessionId("elsewhere"), world.other.dir, SessionState.Working, "elsewhere", T0)
            showWindow(world.projects, world.fake(elsewhere))
            await(WindowTags.projectRow("project0002")).performClick()
            await(WindowTags.missionRow("elsewhere"))

            // Then its row has no badge: there is no "place" in a project without subfolders
            tagged("missionControl.rowPlace.") shouldBe 0
        }
}
