package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.ProjectsMenuState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Wznów sesję" (FR-P9, AM-313): only for a selected disk session, never during a dialog. */
class SessionResumeCommandTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val idle = ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)
    private val intents = mutableListOf<MissionControlIntent>()

    private fun commands(
        actions: SessionActionsState,
        menu: ProjectsMenuState = idle,
    ): List<Command> = sessionCommands(listOf(agent), menu, actions, null, { intents += it }, { })

    private val none = SessionActionsState(canStop = false, canRespawn = false)
    private val diskSession = none.copy(canRemove = true, canResume = true)

    private fun List<Command>.ids(): List<String> = map { it.id.value }

    @Test
    fun `resume is offered only when canResume`() {
        commands(diskSession).ids() shouldContain "sessions.resume"
        commands(none).ids() shouldNotContain "sessions.resume"
        commands(none.copy(canStop = true)).ids() shouldNotContain "sessions.resume"
        commands(none.copy(canRespawn = true, canRemove = true)).ids() shouldNotContain "sessions.resume"
    }

    @Test
    fun `resume has a Polish title, the session group, no shortcut and sends ResumeSelected`() =
        runTest {
            val resume = commands(diskSession).single { it.id.value == "sessions.resume" }

            resume.title shouldBe "Wznów sesję"
            resume.group shouldBe CommandGroup.SESSION
            resume.keywords shouldContain "resume"
            resume.shortcut shouldBe null

            resume.run()

            intents shouldBe listOf(MissionControlIntent.ResumeSelected)
        }

    @Test
    fun `no resume while a dialog of Mission Control is open`() {
        commands(diskSession.copy(dialogOpen = true)).ids() shouldNotContain "sessions.resume"
    }

    @Test
    fun `no resume while a dialog of the project list is open`() {
        val dialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)

        commands(diskSession, dialog).ids() shouldNotContain "sessions.resume"
    }

    @Test
    fun `a disk session offers resume and remove but not respawn`() {
        val ids = commands(diskSession).ids()

        ids shouldContain "sessions.remove"
        ids shouldNotContain "sessions.respawn"
    }

    @Test
    fun `the text of the command exists`() {
        Strings.keys shouldContain "command.resumeSession"
    }
}
