package dev.quayside.app

import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.ui.TerminalSettingsController
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Ustawienia terminala" and its guard against open dialogs (FR-T7, AM-508). */
class TerminalSettingsCommandsTest {
    private fun TestScope.settings() = TerminalSettingsController(FakeTerminalSettingsRepo(), backgroundScope)

    @Test
    fun `offers terminal settings when no dialog is open`() =
        runTest {
            val commands = terminalSettingsCommands(dialogOpen = false, settings())

            val command = commands.single()
            command.id shouldBe CommandId("terminal.settings")
            command.title shouldBe Strings["command.terminalSettings"]
            command.group shouldBe CommandGroup.APP
        }

    @Test
    fun `offers nothing while a dialog is open`() =
        runTest {
            terminalSettingsCommands(dialogOpen = true, settings()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the dialog`() =
        runTest {
            val settings = settings()
            settings.state.value.shouldBeNull()

            terminalSettingsCommands(dialogOpen = false, settings).single().run()

            settings.state.value.shouldNotBeNull()
        }
}
