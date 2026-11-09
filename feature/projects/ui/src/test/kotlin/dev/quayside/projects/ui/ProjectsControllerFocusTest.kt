package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * What the sidebar has selected when a project is a group (FR-P7, AM-306): the project row or one of its subfolder
 * rows. [ProjectsController.selection] stays the project; [ProjectsController.focus] adds the subfolder.
 */
class ProjectsControllerFocusTest {
    private val soft = SubfolderName("soft")
    private val docs = SubfolderName("docs")
    private val plant =
        aProject(
            id = "project0001",
            dir = Path.of("/Users/test/projects/plant-puls"),
            displayName = "Plant",
            subfolders = subfolderNames("docs", "soft"),
        )
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")

    private fun world(): ControllerWorld = ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, plain)))

    private fun TestScope.focusSeenBy(controller: ProjectsController): List<ProjectFocus?> {
        val seen = mutableListOf<ProjectFocus?>()
        backgroundScope.launch { controller.focus.collect { seen += it } }
        act { }
        return seen
    }

    @Test
    fun `nothing is focused at the start`() =
        runTest {
            start(world()).focus.value.shouldBeNull()
        }

    @Test
    fun `select subfolder sets focus`() =
        runTest {
            val controller = start(world())

            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            controller.focus.value shouldBe ProjectFocus(plant.id, soft)
            controller.selection.value shouldBe plant.id
            controller.state.value.selected shouldBe plant.id
        }

    @Test
    fun `select subfolder from the palette sets focus`() =
        runTest {
            val controller = start(world())

            act { controller.onIntent(ProjectsIntent.Select(plant.id, docs)) }

            controller.focus.value shouldBe ProjectFocus(plant.id, docs)
        }

    @Test
    fun `select of the selected project clears the subfolder`() =
        runTest {
            val controller = start(world())
            val seen = focusSeenBy(controller)
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onEvent(SidebarEvent.Select(plant.id)) }

            controller.focus.value shouldBe ProjectFocus(plant.id)
            // StateFlow emitted the change, so Mission Control can leave the subfolder page
            seen.shouldContainInOrder(ProjectFocus(plant.id, soft), ProjectFocus(plant.id, null))
        }

    @Test
    fun `select of the selected project from the palette clears the subfolder`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(plant.id, soft)) }

            act { controller.onIntent(ProjectsIntent.Select(plant.id)) }

            controller.focus.value shouldBe ProjectFocus(plant.id)
        }

    @Test
    fun `another subfolder replaces the focus`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onEvent(SidebarEvent.Select(plant.id, docs)) }

            controller.focus.value shouldBe ProjectFocus(plant.id, docs)
        }

    @Test
    fun `another project drops the subfolder`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onEvent(SidebarEvent.Select(plain.id)) }

            controller.focus.value shouldBe ProjectFocus(plain.id)
        }

    @Test
    fun `a subfolder outside the group is not focused`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id)) }

            act { controller.onEvent(SidebarEvent.Select(plant.id, SubfolderName("marketing"))) }
            act { controller.onIntent(ProjectsIntent.Select(plant.id, SubfolderName("marketing"))) }
            act { controller.onEvent(SidebarEvent.Select(plain.id, soft)) }

            controller.focus.value shouldBe ProjectFocus(plant.id)
        }

    @Test
    fun `a subfolder of an unlisted project is not focused`() =
        runTest {
            val controller = start(world())

            act { controller.onIntent(ProjectsIntent.Select(ProjectId("nope"), soft)) }

            controller.focus.value.shouldBeNull()
        }

    @Test
    fun `select subfolder is ignored while a dialog is open`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plain.id)) }
            act { controller.onEvent(SidebarEvent.RenameRequested(plain.id)) }

            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }
            act { controller.onIntent(ProjectsIntent.Select(plant.id, soft)) }

            controller.focus.value shouldBe ProjectFocus(plain.id)
        }

    @Test
    fun `collapsing the group of the selected subfolder selects the project`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onEvent(SidebarEvent.ToggleGroup(plant.id)) }

            controller.state.value.collapsed shouldBe setOf(plant.id)
            controller.focus.value shouldBe ProjectFocus(plant.id)
        }

    @Test
    fun `expanding the group again keeps the project selected`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }
            act { controller.onEvent(SidebarEvent.ToggleGroup(plant.id)) }

            act { controller.onEvent(SidebarEvent.ToggleGroup(plant.id)) }

            controller.state.value.collapsed shouldBe emptySet()
            controller.focus.value shouldBe ProjectFocus(plant.id)
        }

    @Test
    fun `collapsing another group keeps the focus`() =
        runTest {
            val other =
                aProject(
                    id = "project0003",
                    dir = Path.of("/Users/test/projects/other"),
                    subfolders = subfolderNames("api"),
                )
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, other)))
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onEvent(SidebarEvent.ToggleGroup(other.id)) }

            controller.state.value.collapsed shouldBe setOf(other.id)
            controller.focus.value shouldBe ProjectFocus(plant.id, soft)
        }

    @Test
    fun `groups start expanded`() =
        runTest {
            start(world()).state.value.collapsed shouldBe emptySet()
        }

    @Test
    fun `toggling a project without subfolders is ignored`() =
        runTest {
            val controller = start(world())

            act { controller.onEvent(SidebarEvent.ToggleGroup(plain.id)) }
            act { controller.onEvent(SidebarEvent.ToggleGroup(ProjectId("nope"))) }

            controller.state.value.collapsed shouldBe emptySet()
        }

    @Test
    fun `subfolder that disappears from the project clears focus to the project`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            world.projects.setSubfolders(plant.id, subfolderNames("docs"))
            act { }

            controller.focus.value shouldBe ProjectFocus(plant.id)
            controller.selection.value shouldBe plant.id
        }

    @Test
    fun `focus stays while the subfolder is still in the group`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            world.projects.setSubfolders(plant.id, subfolderNames("soft"))
            act { }

            controller.focus.value shouldBe ProjectFocus(plant.id, soft)
        }

    @Test
    fun `group dissolved by an empty list clears focus to the project`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(plant.id, docs)) }

            world.projects.setSubfolders(plant.id, emptyList())
            act { }

            controller.focus.value shouldBe ProjectFocus(plant.id)
        }

    @Test
    fun `removed project clears the focus`() =
        runTest {
            val world = world()
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            world.projects.remove(plant.id)
            act { }

            controller.focus.value.shouldBeNull()
            controller.state.value.subfolder
                .shouldBeNull()
        }

    @Test
    fun `clear selection clears the subfolder too`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }

            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            controller.focus.value.shouldBeNull()
            controller.state.value.subfolder
                .shouldBeNull()
        }

    @Test
    fun `selecting a project after clear selection starts on the project row`() =
        runTest {
            val controller = start(world())
            act { controller.onEvent(SidebarEvent.Select(plant.id, soft)) }
            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            act { controller.onEvent(SidebarEvent.Select(plant.id)) }

            controller.focus.value shouldBe ProjectFocus(plant.id)
        }
}
