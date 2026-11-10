package dev.quayside.projects.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.math.abs

/**
 * Layout of a project row of the sidebar (FR-P6, AM-310 as corrected): the chips stand on the line of the project
 * name, right after it (a gap of 8 dp, not at the right edge of the row), the row is as high with chips as without,
 * and in the narrow sidebar (260 dp) a long name is shortened while all three chips stay whole inside the row. Runs
 * at 1 px per dp, so the bounds are in dp.
 */
@OptIn(ExperimentalTestApi::class)
internal class SidebarRowLayoutTest {
    private val busy = aProject(id = "project0001", dir = Path.of("/Users/test/projects/busy"), displayName = "Busy")
    private val quiet = aProject(id = "project0002", dir = Path.of("/Users/test/projects/quiet"), displayName = "Quiet")
    private val long =
        aProject(
            id = "project0003",
            dir = Path.of("/Users/test/projects/long"),
            displayName = "A very long project name that cannot possibly fit into the narrow sidebar of the window",
        )

    private fun world(vararg sessions: WorldSession) =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(busy, quiet, long)), running = sessions.toList())

    private fun sessionsOf(
        dir: Path,
        vararg states: SessionState,
    ) = states.mapIndexed { i, state -> WorldSession(aSessionId("${dir.fileName}$i"), dir, state) }

    private fun allThree(dir: Path) =
        sessionsOf(
            dir,
            SessionState.AwaitingInput(null),
            SessionState.Working,
            SessionState.Working,
            SessionState.Failed,
        )

    private fun ComposeUiTest.show(vararg sessions: WorldSession) {
        showProjects(world(*sessions)) { ProjectsSidebar(it) }
    }

    /**
     * The node of the project name: a child of the row that clears its semantics (the row says the name itself) and
     * has no description, which tells it from the chips (they clear the semantics too, but describe themselves).
     */
    private fun ComposeUiTest.nameBounds(project: Project): Rect {
        val row = hasTestTag(ProjectsTags.row(project.id))
        val name =
            SemanticsMatcher("clears semantics, no description") {
                it.config.isClearingSemantics && SemanticsProperties.ContentDescription !in it.config
            } and hasAnyAncestor(row)
        waitUntil { onAllNodes(name, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        return onAllNodes(name, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
    }

    /** Bounds of the chips of [row], left to right. */
    private fun ComposeUiTest.chipBounds(
        row: String,
        count: Int,
    ): List<Rect> {
        val chips = hasContentDescription(": ", substring = true) and hasAnyAncestor(hasTestTag(row))
        waitUntil { onAllNodes(chips, useUnmergedTree = true).fetchSemanticsNodes().size == count }
        return onAllNodes(chips, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.left }
    }

    private fun ComposeUiTest.rowBounds(row: String): Rect = awaitTag(row).fetchSemanticsNode().boundsInRoot

    @Test
    fun `chips stand on the line of the project name`() =
        runComposeUiTest {
            // Given a project with waiting, working and failed sessions
            show(*allThree(busy.dir).toTypedArray())
            val row = ProjectsTags.row(busy.id)

            // When the name and the chips are laid out
            val name = nameBounds(busy)
            val chips = chipBounds(row, count = 3)

            // Then every chip is centred on the name's line (not under it) ...
            chips.forEach { chip ->
                abs(chip.center.y - name.center.y) shouldBeLessThanOrEqualTo CENTER_TOLERANCE
                // ... so it starts above the bottom of the name
                chip.top shouldBeLessThanOrEqualTo name.bottom - 1f
            }
        }

    @Test
    fun `first chip starts 8 dp after the name and far from the right edge of the row`() =
        runComposeUiTest {
            // Given a project with one chip (a working session), in a row 236 dp wide
            show(*sessionsOf(busy.dir, SessionState.Working).toTypedArray())
            val row = ProjectsTags.row(busy.id)

            // When the name, the first chip and the row are measured
            val name = nameBounds(busy)
            val first = chipBounds(row, count = 1).single()
            val edge = rowBounds(row)

            // Then the chip is right of the name with a gap of 8 dp ...
            first.left shouldBe (name.right + GAP plusOrMinus GAP_TOLERANCE)
            // ... and it ends far from the right edge of the row: the chip follows the name, it is not pushed aside
            (edge.right - first.right) shouldBeGreaterThanOrEqualTo MIN_FREE_RIGHT
        }

    @Test
    fun `row with chips is exactly as high as a row without`() =
        runComposeUiTest {
            // Given a project with chips and one with none (a stopped session only) in the same sidebar
            show(*allThree(busy.dir).toTypedArray(), *sessionsOf(quiet.dir, SessionState.Stopped).toTypedArray())
            val busyRow = ProjectsTags.row(busy.id)
            val quietRow = ProjectsTags.row(quiet.id)
            chipBounds(busyRow, count = 3)

            // Then both rows are 32 dp high: 20 dp of the folder icon and of the chips, plus 2 x 6 dp of padding. The
            // chips stand on the line of the name, so they add no height of their own
            rowBounds(busyRow).height shouldBe 32f
            rowBounds(busyRow).height shouldBe rowBounds(quietRow).height
        }

    @Test
    fun `long name is shortened in the narrow sidebar and all three chips stay whole inside the row`() =
        runComposeUiTest {
            // Given a project with a very long name and all three chips
            show(*allThree(long.dir).toTypedArray())
            val row = ProjectsTags.row(long.id)

            // When the row is laid out in the 260 dp sidebar
            val chips = chipBounds(row, count = 3)
            val edge = rowBounds(row)
            val name = nameBounds(long)

            // Then the name is cut: it takes all the room the chips leave, so it ends 8 dp before the first chip
            // (uncut, a name of 90 characters would run far past the row) ...
            name.right shouldBe (chips.first().left - GAP plusOrMinus GAP_TOLERANCE)
            // ... the chips do not overlap and none is clipped by the edge of the row
            chips.zipWithNext().forEach { (a, b) -> a.right shouldBeLessThanOrEqualTo b.left }
            chips.forEach { chip ->
                chip.left shouldBeGreaterThanOrEqualTo edge.left
                chip.right shouldBeLessThanOrEqualTo edge.right - ROW_PADDING
                chip.width shouldBeGreaterThanOrEqualTo MIN_CHIP_WIDTH
            }
        }

    private companion object {
        const val CENTER_TOLERANCE = 3f
        const val GAP = 8f
        const val GAP_TOLERANCE = 1.5f
        const val MIN_FREE_RIGHT = 100f
        const val MIN_CHIP_WIDTH = 20f
        const val ROW_PADDING = 8f
    }
}
