package dev.quayside.projects.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performMouseInput
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The folder tree of a group in the sidebar (FR-P6, FR-P7), read from pixels at 4 px per dp: a vertical guide at 43 dp
 * runs along the subfolder rows and a horizontal branch reaches from it to 54 dp; the last subfolder ends the guide in
 * the middle of its row (an "L"), the others continue it to the bottom (a "T"). The content of a subfolder row starts
 * at 58 dp. The guide is decoration: it changes neither the semantics of the row nor the focus nor where a click lands.
 * The group `plant` has the subfolders `Anna`, `bob` and `cara` (in that order); `solo` has one.
 */
@OptIn(ExperimentalTestApi::class)
internal class SidebarTreeGuideTest {
    private val plant =
        aProject(
            id = "project0001",
            dir = Path.of("/Users/test/projects/plant-puls"),
            displayName = "Plant",
            subfolders = subfolderNames("bob", "Anna", "cara"),
        )
    private val solo =
        aProject(
            id = "project0002",
            dir = Path.of("/Users/test/projects/solo"),
            displayName = "Solo",
            subfolders = subfolderNames("only"),
        )
    private val plain = aProject(id = "project0003", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")
    private val guide =
        SampleTokens
            .colors()
            .surfaces.border2
            .argb()
    private val annaRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("Anna"))
    private val bobRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("bob"))
    private val caraRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("cara"))
    private val onlyRow = ProjectsTags.subfolderRow(solo.id, SubfolderName("only"))

    private fun ComposeUiTest.show(): ProjectsController =
        showProjects(ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, solo, plain)))) {
            Scaled4 { ProjectsSidebar(it) }
        }

    private fun ComposeUiTest.pixels(row: String): PixelMap = awaitTag(row).pixelMap()

    /** Whether the pixel column [x] is the guide colour at [y]. */
    private fun PixelMap.isGuide(
        x: Int,
        y: Int,
    ): Boolean = argbAt(x, y) == guide

    /** The first column from [from] dp on that has any pixel unlike the empty corner of the row; null if none. */
    private fun PixelMap.firstMarkX(from: Int): Int? {
        val empty = argbAt(width - 2, 2)
        return (from * SCALE until width).firstOrNull { x -> (0 until height).any { argbAt(x, it) != empty } }
    }

    @Test
    fun `guide runs the whole height of a subfolder row that is not the last`() =
        runTallTest {
            show()

            listOf(annaRow, bobRow).forEach { row ->
                val map = pixels(row)
                (0 until map.height).forEach { y -> map.isGuide(GUIDE_PX, y) shouldBe true }
            }
        }

    @Test
    fun `guide of the last subfolder ends in the middle of its row`() =
        runTallTest {
            show()

            val map = pixels(caraRow)
            val middle = map.height / 2
            (0 until middle - BRANCH_PX).forEach { y -> map.isGuide(GUIDE_PX, y) shouldBe true }
            (middle + BRANCH_PX until map.height).forEach { y -> map.isGuide(GUIDE_PX, y) shouldBe false }
        }

    @Test
    fun `the only subfolder of a project is an L`() =
        runTallTest {
            show()

            val map = pixels(onlyRow)
            val middle = map.height / 2
            map.isGuide(GUIDE_PX, 0) shouldBe true
            map.isGuide(GUIDE_PX, map.height - 1) shouldBe false
            map.isGuide(GUIDE_PX, middle + BRANCH_PX + 1) shouldBe false
        }

    @Test
    fun `every subfolder has a branch from the guide to 54 dp and not beyond`() =
        runTallTest {
            show()

            listOf(annaRow, bobRow, caraRow, onlyRow).forEach { row ->
                val map = pixels(row)
                val y = map.height / 2
                (GUIDE_PX until END_PX).forEach { x -> map.isGuide(x, y) shouldBe true }
                (END_PX until END_PX + 2 * SCALE).forEach { x -> map.isGuide(x, y) shouldBe false }
                (0 until GUIDE_PX - 2 * SCALE).forEach { x -> map.isGuide(x, y) shouldBe false }
            }
        }

    @Test
    fun `folder icon of a subfolder starts at 58 dp or later, further than the icon of a project`() =
        runTallTest {
            show()

            val sub = pixels(annaRow).firstMarkX(from = 55)
            val project = pixels(ProjectsTags.row(plain.id)).firstMarkX(from = 0)

            checkNotNull(sub) shouldBeGreaterThanOrEqualTo INDENT_PX
            checkNotNull(project) shouldBeGreaterThanOrEqualTo 0
            sub shouldBeGreaterThanOrEqualTo checkNotNull(project) + INDENT_PX / 2
        }

    @Test
    fun `a click on the guide selects the subfolder and the row keeps no focus`() =
        runTallTest {
            val controller = show()
            val row = awaitTag(caraRow)

            row.performMouseInput { click(Offset(GUIDE_PX.toFloat(), center.y)) }
            waitForIdle()

            controller.focus.value shouldBe ProjectFocus(plant.id, SubfolderName("cara"))
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    private companion object {
        const val GUIDE_PX = 43 * SCALE
        const val END_PX = 54 * SCALE
        const val INDENT_PX = 58 * SCALE
        const val BRANCH_PX = 3 * SCALE / 2 + 1
    }
}
