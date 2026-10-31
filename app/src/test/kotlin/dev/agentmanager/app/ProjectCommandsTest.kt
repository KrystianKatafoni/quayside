package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.projects.ui.ProjectsIntent
import dev.agentmanager.projects.ui.ProjectsMenuState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure projection of the project list and the menu state onto palette commands (AM-408, decision 1). */
class ProjectCommandsTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    private val noSelection = ProjectsMenuState(canAdd = true, canRemove = false, canRename = false, dialogOpen = false)
    private val selection = ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)
    private val dialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)

    private fun commands(
        list: List<Project>,
        menu: ProjectsMenuState,
        sent: MutableList<ProjectsIntent> = mutableListOf(),
    ): List<Command> = projectCommands(list, menu) { sent += it }

    private fun List<Command>.ids(): List<String> = map { it.id.value }

    private fun List<Command>.byId(id: String): Command = single { it.id.value == id }

    @Test
    fun `without projects only add is offered`() {
        val result = commands(emptyList(), noSelection)

        result.ids() shouldBe listOf("projects.add")
        result.single().title shouldBe "Dodaj projekt…"
        result.single().group shouldBe CommandGroup.PROJECT
    }

    @Test
    fun `a selection adds rename and remove`() {
        val result = commands(listOf(agent), selection)

        result.ids() shouldBe listOf("projects.add", "projects.rename", "projects.remove", "projects.goto.project0001")
        result.byId("projects.rename").title shouldBe "Zmień nazwę projektu…"
        result.byId("projects.rename").keywords shouldBe listOf("rename", "nazwa")
        result.byId("projects.rename").shortcut.shouldBeNull()
        result.byId("projects.remove").title shouldBe "Usuń projekt"
        result.byId("projects.remove").keywords shouldBe listOf("remove")
        result.byId("projects.remove").group shouldBe CommandGroup.PROJECT
    }

    @Test
    fun `projects without a selection get go to commands but no rename or remove`() {
        commands(listOf(agent), noSelection).ids() shouldBe listOf("projects.add", "projects.goto.project0001")
    }

    @Test
    fun `every project has a go to command with its name`() {
        val result = commands(listOf(agent, other), noSelection)

        result.byId("projects.goto.project0001").title shouldBe "Przejdź do projektu: Agent Manager"
        result.byId("projects.goto.project0002").title shouldBe "Przejdź do projektu: Other"
        result.byId("projects.goto.project0002").group shouldBe CommandGroup.GO_TO_PROJECT
        result.byId("projects.goto.project0002").shortcut.shouldBeNull()
    }

    @Test
    fun `an open dialog gives no commands`() {
        commands(listOf(agent, other), dialog).shouldBeEmpty()
    }

    @Test
    fun `go to command selects the project`() =
        runTest {
            val sent = mutableListOf<ProjectsIntent>()

            commands(listOf(agent, other), noSelection, sent).byId("projects.goto.project0002").run()

            sent shouldBe listOf(ProjectsIntent.Select(ProjectId("project0002")))
        }

    @Test
    fun `rename command sends RenameSelectedRequested`() =
        runTest {
            val sent = mutableListOf<ProjectsIntent>()

            commands(listOf(agent), selection, sent).byId("projects.rename").run()

            sent shouldBe listOf(ProjectsIntent.RenameSelectedRequested)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intents")
    fun `add and remove commands send their intents`(
        id: String,
        expected: ProjectsIntent,
    ) = runTest {
        val sent = mutableListOf<ProjectsIntent>()

        commands(listOf(agent), selection, sent).byId(id).run()

        sent shouldBe listOf(expected)
    }

    @Test
    fun `command ids are unique`() {
        val ids = commands(listOf(agent, other, aProject(id = "project0003")), selection).ids()

        ids.toSet().size shouldBe ids.size
    }

    @Test
    fun `command shortcuts equal menu shortcuts`() {
        val result = commands(listOf(agent), selection)

        result.byId("projects.add").shortcut shouldBe AppShortcuts.ADD_PROJECT
        result.byId("projects.remove").shortcut shouldBe AppShortcuts.REMOVE_PROJECT
    }

    companion object {
        @JvmStatic
        fun intents(): List<Arguments> =
            listOf(
                arguments("projects.add", ProjectsIntent.AddRequested),
                arguments("projects.remove", ProjectsIntent.RemoveSelectedRequested),
            )
    }
}
