package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.ProjectsMenuState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The pure projection of the project list and menu state onto session and navigation commands (AM-409). */
class SessionCommandsTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

    private val idle = ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)
    private val dialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)

    private val intents = mutableListOf<MissionControlIntent>()
    private val opened = mutableListOf<CommandGroup>()

    private fun commands(
        list: List<Project> = listOf(agent),
        menu: ProjectsMenuState = idle,
        actions: SessionActionsState = SessionActionsState(canStop = false, canRespawn = false),
    ): List<Command> = sessionCommands(list, menu, actions, null, { intents += it }, { opened += it })

    private val canStop = SessionActionsState(canStop = true, canRespawn = false)
    private val canRespawn = SessionActionsState(canStop = false, canRespawn = true)

    private fun List<Command>.ids(): List<String> = map { it.id.value }

    private fun List<Command>.byId(id: String): Command = single { it.id.value == id }

    @Test
    fun `j and k send next and previous session`() =
        runTest {
            val result = commands()

            result.byId("sessions.next").title shouldBe "Następna sesja"
            result.byId("sessions.next").group shouldBe CommandGroup.SESSION
            result.byId("sessions.next").shortcut shouldBe AppShortcuts.NEXT_SESSION
            result.byId("sessions.previous").title shouldBe "Poprzednia sesja"
            result.byId("sessions.previous").group shouldBe CommandGroup.SESSION
            result.byId("sessions.previous").shortcut shouldBe AppShortcuts.PREVIOUS_SESSION

            result.byId("sessions.next").run()
            result.byId("sessions.previous").run()

            intents shouldBe listOf(MissionControlIntent.NextSession, MissionControlIntent.PreviousSession)
        }

    @Test
    fun `j and k are offered without any project`() {
        commands(list = emptyList()).ids() shouldContain "sessions.next"
        commands(list = emptyList()).ids() shouldContain "sessions.previous"
    }

    @Test
    fun `g p opens the palette limited to go to project`() =
        runTest {
            val switch = commands().byId("projects.switch")

            switch.title shouldBe "Przełącz projekt…"
            switch.keywords shouldBe listOf("switch", "projekt")
            switch.shortcut shouldBe AppShortcuts.SWITCH_PROJECT

            switch.run()

            opened shouldBe listOf(CommandGroup.GO_TO_PROJECT)
            intents.shouldBeEmpty()
        }

    @Test
    fun `switch project is not in the go to project group`() {
        // Otherwise the palette opened by g p would list the command that opened it.
        commands().byId("projects.switch").group shouldBe CommandGroup.PROJECT
    }

    @Test
    fun `switch project needs a project`() {
        commands(list = emptyList()).ids() shouldNotContain "projects.switch"
        commands(list = listOf(agent)).ids() shouldContain "projects.switch"
    }

    @Test
    fun `an open dialog gives no session commands`() {
        commands(menu = dialog).shouldBeEmpty()
    }

    @Test
    fun `x sends StopSelected`() =
        runTest {
            val stop = commands(actions = canStop).byId("sessions.stop")

            stop.title shouldBe "Zatrzymaj sesję"
            stop.group shouldBe CommandGroup.SESSION
            stop.keywords shouldBe listOf("stop")
            stop.shortcut shouldBe AppShortcuts.STOP_SESSION

            stop.run()

            intents shouldBe listOf(MissionControlIntent.StopSelected)
        }

    @Test
    fun `stop is offered only when canStop`() {
        commands(actions = canStop).ids() shouldContain "sessions.stop"
        commands(actions = canRespawn).ids() shouldNotContain "sessions.stop"
        commands().ids() shouldNotContain "sessions.stop"
    }

    @Test
    fun `respawn is offered only when canRespawn`() =
        runTest {
            commands(actions = canStop).ids() shouldNotContain "sessions.respawn"
            commands().ids() shouldNotContain "sessions.respawn"

            val respawn = commands(actions = canRespawn).byId("sessions.respawn")

            respawn.title shouldBe "Respawn sesji"
            respawn.group shouldBe CommandGroup.SESSION
            respawn.keywords shouldBe listOf("respawn", "wznów")
            respawn.shortcut shouldBe null

            respawn.run()

            intents shouldBe listOf(MissionControlIntent.RespawnSelected)
        }

    @Test
    fun `an open dialog gives no stop or respawn`() {
        val both = SessionActionsState(canStop = true, canRespawn = true)

        commands(menu = dialog, actions = both).shouldBeEmpty()
    }

    private val canRemove = SessionActionsState(canStop = false, canRespawn = true, canRemove = true)
    private val dialogOpen =
        SessionActionsState(canStop = true, canRespawn = true, canRemove = true, dialogOpen = true)

    @Test
    fun `remove is offered only when canRemove`() =
        runTest {
            commands(actions = canStop).ids() shouldNotContain "sessions.remove"
            commands(actions = canRespawn).ids() shouldNotContain "sessions.remove"
            commands().ids() shouldNotContain "sessions.remove"

            val remove = commands(actions = canRemove).byId("sessions.remove")

            remove.title shouldBe "Usuń sesję z listy"
            remove.group shouldBe CommandGroup.SESSION
            remove.keywords shouldBe listOf("remove", "rm", "usuń")
            remove.shortcut shouldBe null
        }

    @Test
    fun `remove sends RemoveSelected`() =
        runTest {
            commands(actions = canRemove).byId("sessions.remove").run()

            intents shouldBe listOf(MissionControlIntent.RemoveSelected)
        }

    @Test
    fun `an open mission control dialog gives no session commands`() {
        commands(actions = dialogOpen).shouldBeEmpty()
        commands(list = emptyList(), actions = dialogOpen).shouldBeEmpty()
    }

    @Test
    fun `stop and respawn are offered without any project`() {
        commands(list = emptyList(), actions = canStop).ids() shouldContain "sessions.stop"
        commands(list = emptyList(), actions = canRespawn).ids() shouldContain "sessions.respawn"
    }

    @Test
    fun `with stop offered four commands carry a shortcut`() {
        commands(actions = canStop).filter { it.shortcut != null }.ids().toSet() shouldBe
            setOf("sessions.next", "sessions.previous", "sessions.stop", "projects.switch")
    }

    @Test
    fun `command ids are unique and keep clear of project commands`() {
        val ids = commands(listOf(agent, aProject(id = "project0002"))).ids()

        ids.toSet().size shouldBe ids.size
        ids.none { it.startsWith("projects.goto.") || it == "projects.add" } shouldBe true
    }

    @Test
    fun `only the three keyboard commands carry a shortcut`() {
        commands().filter { it.shortcut != null }.ids().toSet() shouldBe
            setOf("sessions.next", "sessions.previous", "projects.switch")
    }

    private val canPreview = SessionActionsState(canStop = true, canRespawn = false, canPreview = true)
    private val previewOpen = SessionActionsState(canStop = false, canRespawn = false, previewOpen = true)

    @Test
    fun `preview is offered only when canPreview or previewOpen`() {
        commands().ids() shouldNotContain "sessions.preview"
        commands(actions = canStop).ids() shouldNotContain "sessions.preview"
        commands(actions = canPreview).ids() shouldContain "sessions.preview"
        commands(actions = previewOpen).ids() shouldContain "sessions.preview"
        commands(list = emptyList(), actions = canPreview).ids() shouldContain "sessions.preview"
    }

    @Test
    fun `preview title follows previewOpen`() {
        val closed = commands(actions = canPreview).byId("sessions.preview")
        val open = commands(actions = previewOpen).byId("sessions.preview")

        closed.title shouldBe "Podgląd outputu"
        open.title shouldBe "Zamknij podgląd"
        listOf(closed, open).forEach {
            it.group shouldBe CommandGroup.SESSION
            it.keywords shouldBe listOf("preview", "logs", "podgląd")
            it.shortcut shouldBe AppShortcuts.PREVIEW_SESSION
        }
    }

    @Test
    fun `preview sends TogglePreview`() =
        runTest {
            commands(actions = canPreview).byId("sessions.preview").run()
            commands(actions = previewOpen).byId("sessions.preview").run()

            intents shouldBe listOf(MissionControlIntent.TogglePreview, MissionControlIntent.TogglePreview)
        }

    @Test
    fun `an open dialog gives no preview command`() {
        val withDialog = SessionActionsState(canStop = false, canRespawn = false, previewOpen = true, dialogOpen = true)

        commands(actions = withDialog).ids() shouldNotContain "sessions.preview"
        commands(menu = dialog, actions = canPreview).ids() shouldNotContain "sessions.preview"
    }
}
