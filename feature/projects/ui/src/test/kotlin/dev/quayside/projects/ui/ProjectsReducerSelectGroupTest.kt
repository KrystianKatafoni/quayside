package dev.quayside.projects.ui

import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Selecting in a collapsed group (FR-P7, FR-P8, AM-307): a subfolder shows its row, so the group must open. */
class ProjectsReducerSelectGroupTest {
    private val plant =
        aProject(id = "plant", dir = Path.of("/p/plant"), displayName = "Plant", subfolders = subfolderNames("soft"))
    private val farm =
        aProject(id = "farm", dir = Path.of("/p/farm"), displayName = "Farm", subfolders = subfolderNames("barn"))
    private val soft = SubfolderName("soft")

    private val collapsed = ProjectsUiState(projects = listOf(plant, farm), collapsed = setOf(plant.id, farm.id))

    @Test
    fun `selecting a subfolder of a collapsed group expands it`() {
        val state = collapsed.reduce(SidebarEvent.Select(plant.id, soft))

        state.selected shouldBe plant.id
        state.subfolder shouldBe soft
        state.collapsed shouldBe setOf(farm.id)
    }

    @Test
    fun `selecting the project of a collapsed group keeps it collapsed`() {
        val state = collapsed.reduce(SidebarEvent.Select(plant.id))

        state.selected shouldBe plant.id
        state.subfolder shouldBe null
        state.collapsed shouldBe setOf(plant.id, farm.id)
    }

    @Test
    fun `selecting an unknown subfolder keeps the group collapsed`() {
        val state = collapsed.reduce(SidebarEvent.Select(plant.id, SubfolderName("nope")))

        state shouldBe collapsed
    }

    @Test
    fun `selecting a subfolder of an expanded group changes nothing about the groups`() {
        val open = collapsed.copy(collapsed = setOf(farm.id))

        open.reduce(SidebarEvent.Select(plant.id, soft)).collapsed shouldBe setOf(farm.id)
    }
}
