package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.missioncontrol.ui.GlobalView
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.ProjectsMenuState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The palette commands of the global views and the hidden filter commands (FR-K1, FR-S9; AM-407, decision 6). */
class SessionViewCommandsTest {
    private val idle = ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)
    private val intents = mutableListOf<MissionControlIntent>()
    private val noActions = SessionActionsState(canStop = false, canRespawn = false)

    private fun commands(
        view: GlobalView? = null,
        menu: ProjectsMenuState = idle,
        actions: SessionActionsState = noActions,
        projects: List<Project> = listOf(aProject()),
    ): List<Command> = sessionCommands(projects, menu, actions, view, { intents += it }, { })

    private fun List<Command>.ids(): List<String> = map { it.id.value }

    private val viewIds = listOf("sessions.view.waiting", "sessions.view.working", "sessions.view.failed")

    @ParameterizedTest(name = "{0}")
    @MethodSource("views")
    fun `view command sends ShowView`(
        id: String,
        title: String,
        keywords: List<String>,
        view: GlobalView,
    ) = runTest {
        val command = commands().single { it.id.value == id }

        command.title shouldBe title
        command.group shouldBe CommandGroup.SESSION
        command.keywords shouldBe keywords
        command.shortcut.shouldBeNull()

        command.run()

        intents shouldBe listOf(MissionControlIntent.ShowView(view))
    }

    @Test
    fun `view commands are offered except the shown view`() {
        commands(view = null).ids() shouldContainAll viewIds
        commands(view = GlobalView.WAITING).ids().filter { it in viewIds } shouldBe
            listOf("sessions.view.working", "sessions.view.failed")
        commands(view = GlobalView.WORKING).ids().filter { it in viewIds } shouldBe
            listOf("sessions.view.waiting", "sessions.view.failed")
        commands(view = GlobalView.FAILED).ids().filter { it in viewIds } shouldBe
            listOf("sessions.view.waiting", "sessions.view.working")
    }

    @Test
    fun `view commands are offered without any project`() {
        commands(projects = emptyList()).ids() shouldContainAll viewIds
    }

    @Test
    fun `filter commands are hidden in a global view`() {
        val filters = listOf("sessions.filter.all", "sessions.filter.active", "sessions.filter.finished")

        commands(view = null).ids() shouldContainAll filters
        GlobalView.entries.forEach { view ->
            val ids = commands(view = view).ids()
            filters.forEach { ids shouldNotContain it }
            // The keys and the project switch stay.
            ids shouldContain "sessions.next"
            ids shouldContain "sessions.previous"
            ids shouldContain "projects.switch"
        }
    }

    @Test
    fun `no view commands while a dialog is open`() {
        val projectDialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)
        val sessionDialog = SessionActionsState(canStop = false, canRespawn = true, canRemove = true, dialogOpen = true)

        commands(menu = projectDialog).ids().filter { it in viewIds } shouldBe emptyList()
        commands(actions = sessionDialog).ids().filter { it in viewIds } shouldBe emptyList()
        commands(view = GlobalView.WORKING, menu = projectDialog).ids().filter { it in viewIds } shouldBe emptyList()
    }

    @Test
    fun `view commands carry no shortcut`() {
        commands().filter { it.id.value in viewIds }.all { it.shortcut == null } shouldBe true
    }

    companion object {
        @JvmStatic
        fun views(): List<Arguments> =
            listOf(
                arguments(
                    "sessions.view.waiting",
                    "Widok: Czeka na mnie",
                    listOf("waiting", "czeka"),
                    GlobalView.WAITING,
                ),
                arguments("sessions.view.working", "Widok: Pracuje", listOf("working"), GlobalView.WORKING),
                arguments(
                    "sessions.view.failed",
                    "Widok: Błędy",
                    listOf("failed", "errors", "błąd"),
                    GlobalView.FAILED,
                ),
            )
    }
}
