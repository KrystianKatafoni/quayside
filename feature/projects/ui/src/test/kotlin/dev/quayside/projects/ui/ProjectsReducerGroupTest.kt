package dev.quayside.projects.ui

import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The pure reducer of groups (FR-P7, AM-306): the chevron under a dialog and the memory of collapsed groups. */
class ProjectsReducerGroupTest {
    private val plant =
        aProject(id = "plant", dir = Path.of("/p/plant"), displayName = "Plant", subfolders = subfolderNames("soft"))

    @Test
    fun `the chevron is ignored while a dialog is open`() {
        val open = ProjectsUiState(projects = listOf(plant), dialog = Dialog.Message(UiText("dialog.close")))

        open.reduce(SidebarEvent.ToggleGroup(plant.id)) shouldBe open
    }

    @Test
    fun `a collapsed group that was dissolved and made again is expanded`() {
        val collapsed = ProjectsUiState(projects = listOf(plant), collapsed = setOf(plant.id))

        val dissolved = collapsed.withProjects(listOf(plant.copy(subfolders = emptyList())))
        val again = dissolved.withProjects(listOf(plant))

        dissolved.collapsed shouldBe emptySet()
        again.collapsed shouldBe emptySet()
    }

    @Test
    fun `a collapsed group stays collapsed while it is still a group`() {
        val collapsed = ProjectsUiState(projects = listOf(plant), collapsed = setOf(plant.id))

        collapsed.withProjects(listOf(plant.copy(displayName = "Renamed"))).collapsed shouldBe setOf(plant.id)
    }
}
