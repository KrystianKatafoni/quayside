@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.projects.ui

import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The public selection stream `app` passes to Mission Control (AM-405, decision 6). */
class ProjectsControllerSelectionTest {
    @Test
    fun `selection follows the sidebar and clears when the project leaves the list`() =
        runTest {
            val other = aProject(id = "other", dir = Path.of("/Users/test/projects/other"))
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(aProject(), other)))
            val controller = start(world)
            controller.selection.value.shouldBeNull()

            act { controller.onEvent(SidebarEvent.Select(aProject().id)) }
            controller.selection.value shouldBe aProject().id

            act { controller.onEvent(SidebarEvent.Select(other.id)) }
            controller.selection.value shouldBe other.id

            world.projects.remove(other.id)
            runCurrent()
            controller.selection.value.shouldBeNull()
        }
}
