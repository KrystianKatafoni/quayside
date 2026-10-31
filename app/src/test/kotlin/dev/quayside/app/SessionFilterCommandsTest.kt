package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.missioncontrol.ui.SessionFilter
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.ProjectsMenuState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Filter commands of the palette (FR-K1, FR-S9; AM-409 question 1). Kept apart so they can move to AM-410. */
class SessionFilterCommandsTest {
    private val idle = ProjectsMenuState(canAdd = true, canRemove = true, canRename = true, dialogOpen = false)
    private val intents = mutableListOf<MissionControlIntent>()
    private val noActions = SessionActionsState(canStop = false, canRespawn = false)

    private fun commands(): List<Command> =
        sessionCommands(listOf(aProject()), idle, noActions, null, { intents += it }, { })

    @ParameterizedTest(name = "{0}")
    @MethodSource("filters")
    fun `filter commands send SelectFilter`(
        id: String,
        title: String,
        filter: SessionFilter,
    ) = runTest {
        val command = commands().single { it.id.value == id }

        command.title shouldBe title
        command.group shouldBe CommandGroup.SESSION
        command.shortcut.shouldBeNull()

        command.run()

        intents shouldBe listOf(MissionControlIntent.SelectFilter(filter))
    }

    @Test
    fun `an open dialog gives no filter commands`() {
        val dialog = ProjectsMenuState(canAdd = false, canRemove = false, canRename = false, dialogOpen = true)

        sessionCommands(
            listOf(aProject()),
            dialog,
            noActions,
            null,
            { },
            { },
        ).none { it.id.value.startsWith("sessions.filter.") } shouldBe
            true
    }

    companion object {
        @JvmStatic
        fun filters(): List<Arguments> =
            listOf(
                arguments("sessions.filter.all", "Filtr: Wszystkie", SessionFilter.ALL),
                arguments("sessions.filter.active", "Filtr: Aktywne", SessionFilter.ACTIVE),
                arguments("sessions.filter.finished", "Filtr: Zakończone", SessionFilter.FINISHED),
            )
    }
}
