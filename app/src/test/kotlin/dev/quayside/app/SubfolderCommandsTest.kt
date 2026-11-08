package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsMenuState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The palette commands of a group (FR-P7, FR-K1, AM-306): go to a subfolder and "Podfoldery projektu…". */
class SubfolderCommandsTest {
    private val plant =
        aProject(
            id = "project0001",
            dir = Path.of("/Users/test/projects/plant-puls"),
            displayName = "plant-puls",
            subfolders = subfolderNames("Anna", "bob"),
        )
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "plain")

    private val idle = ProjectsMenuState(canAdd = true, canRemove = false, canRename = false, dialogOpen = false)
    private val selected =
        ProjectsMenuState(
            canAdd = true,
            canRemove = true,
            canRename = true,
            dialogOpen = false,
            canEditSubfolders = true,
        )
    private val dialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)

    private fun commands(
        list: List<Project>,
        menu: ProjectsMenuState,
        sent: MutableList<ProjectsIntent> = mutableListOf(),
    ): List<Command> = projectCommands(list, menu) { sent += it }

    private fun List<Command>.ids(): List<String> = map { it.id.value }

    private fun List<Command>.byId(id: String): Command = single { it.id.value == id }

    @Test
    fun `every subfolder has a go to command named after the project and the subfolder`() {
        val result = commands(listOf(plant, plain), idle)

        result.byId("projects.goto.project0001.sub0").title shouldBe "Przejdź do: plant-puls / Anna"
        result.byId("projects.goto.project0001.sub1").title shouldBe "Przejdź do: plant-puls / bob"
        result.byId("projects.goto.project0001.sub1").group shouldBe CommandGroup.GO_TO_PROJECT
        result.byId("projects.goto.project0001.sub1").shortcut.shouldBeNull()
    }

    @Test
    fun `the go to commands of a group follow its project command, in the order of the subfolders`() {
        commands(listOf(plant, plain), idle).ids() shouldBe
            listOf(
                "projects.add",
                "projects.goto.project0001",
                "projects.goto.project0001.sub0",
                "projects.goto.project0001.sub1",
                "projects.goto.project0002",
            )
    }

    @Test
    fun `no command id holds the name of a subfolder`() {
        val named = plant.copy(subfolders = subfolderNames("jane-secret", "zed-secret"))

        commands(listOf(named), selected).ids().forEach {
            it.contains("secret") shouldBe false
        }
        commands(listOf(named), selected).ids() shouldBe
            listOf(
                "projects.add",
                "projects.rename",
                "projects.remove",
                "projects.subfolders",
                "projects.goto.project0001",
                "projects.goto.project0001.sub0",
                "projects.goto.project0001.sub1",
            )
    }

    @Test
    fun `the project command is unchanged`() {
        commands(listOf(plant), idle).byId("projects.goto.project0001").title shouldBe
            "Przejdź do projektu: plant-puls"
    }

    @Test
    fun `go to a subfolder selects it`() =
        runTest {
            val sent = mutableListOf<ProjectsIntent>()

            commands(listOf(plant), idle, sent).byId("projects.goto.project0001.sub1").run()

            sent shouldBe listOf(ProjectsIntent.Select(plant.id, SubfolderName("bob")))
        }

    @Test
    fun `go to the project selects it without a subfolder`() =
        runTest {
            val sent = mutableListOf<ProjectsIntent>()

            commands(listOf(plant), idle, sent).byId("projects.goto.project0001").run()

            sent shouldBe listOf(ProjectsIntent.Select(plant.id))
        }

    @Test
    fun `a project without subfolders has no subfolder commands`() {
        commands(listOf(plain), idle).ids() shouldBe listOf("projects.add", "projects.goto.project0002")
    }

    @Test
    fun `edit subfolders is offered with a selection`() =
        runTest {
            val sent = mutableListOf<ProjectsIntent>()
            val result = commands(listOf(plant), selected, sent)

            result.byId("projects.subfolders").title shouldBe "Podfoldery projektu…"
            result.byId("projects.subfolders").group shouldBe CommandGroup.PROJECT
            result.byId("projects.subfolders").run()
            sent shouldBe listOf(ProjectsIntent.EditSubfoldersRequested)
        }

    @Test
    fun `edit subfolders is not offered without a selection`() {
        commands(listOf(plant), idle).ids() shouldBe
            listOf(
                "projects.add",
                "projects.goto.project0001",
                "projects.goto.project0001.sub0",
                "projects.goto.project0001.sub1",
            )
    }

    @Test
    fun `edit subfolders is offered next to rename and remove`() {
        commands(
            listOf(plant),
            selected,
        ).ids().shouldContainInOrder("projects.add", "projects.rename", "projects.remove")
        commands(listOf(plant), selected).ids().contains("projects.subfolders") shouldBe true
    }

    @Test
    fun `an open dialog gives no subfolder commands`() {
        commands(listOf(plant, plain), dialog).shouldBeEmpty()
    }

    @Test
    fun `the window menu item follows the controller and the palette`() {
        AppMenuState.of(selected, paletteOpen = false).subfolders shouldBe true
        AppMenuState.of(idle, paletteOpen = false).subfolders shouldBe false
        AppMenuState.of(selected, paletteOpen = true).subfolders shouldBe false
        AppMenuState.of(selected, paletteOpen = false, settingsDialogOpen = true).subfolders shouldBe false
        AppMenuState.of(dialog, paletteOpen = false).subfolders shouldBe false
    }
}
