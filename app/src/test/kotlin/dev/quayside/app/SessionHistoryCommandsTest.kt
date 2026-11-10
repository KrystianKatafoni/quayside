package dev.quayside.app

import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.missioncontrol.ui.HistorySettingsController
import dev.quayside.sessions.api.FakeSessionHistorySettings
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Stare sesje z dysku…" and its guard against open dialogs (FR-P9, AM-312). */
class SessionHistoryCommandsTest {
    private fun TestScope.history() = HistorySettingsController(FakeSessionHistorySettings(), backgroundScope)

    @Test
    fun `offers the old sessions window when no dialog is open`() =
        runTest {
            val command = sessionHistoryCommands(dialogOpen = false, history()).single()

            command.id shouldBe CommandId("sessions.history")
            command.title shouldBe Strings["command.sessionHistory"]
            command.group shouldBe CommandGroup.APP
        }

    @Test
    fun `the command id holds no user data`() =
        runTest {
            // The palette logs the id of every command it runs (ENG-C8): a fixed id, no name, path or title.
            sessionHistoryCommands(dialogOpen = false, history()).single().id.value shouldBe "sessions.history"
        }

    @Test
    fun `hidden while another dialog is open`() =
        runTest {
            sessionHistoryCommands(dialogOpen = true, history()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the window`() =
        runTest {
            val history = history()
            history.state.value.shouldBeNull()

            sessionHistoryCommands(dialogOpen = false, history).single().run()

            history.state.value.shouldNotBeNull()
        }
}
