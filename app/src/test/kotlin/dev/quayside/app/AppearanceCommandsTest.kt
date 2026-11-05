package dev.quayside.app

import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.FakeThemeFiles
import dev.quayside.appearance.ui.AppearanceController
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Wygląd…" and its guard against open dialogs (FR-A3, AM-803). */
class AppearanceCommandsTest {
    private fun TestScope.appearance() =
        AppearanceController(
            FakeAppearanceApi(),
            FakeTerminalSettingsRepo(),
            FakeThemeFiles(),
            NoThemeFilePicker,
            backgroundScope,
        )

    @Test
    fun `offers appearance when no dialog is open`() =
        runTest {
            val commands = appearanceCommands(dialogOpen = false, appearance())

            val command = commands.single()
            command.id shouldBe CommandId("appearance.open")
            command.title shouldBe Strings["command.appearance"]
            command.group shouldBe CommandGroup.APP
        }

    @Test
    fun `hidden while another dialog is open`() =
        runTest {
            appearanceCommands(dialogOpen = true, appearance()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the dialog`() =
        runTest {
            val appearance = appearance()
            appearance.state.value.shouldBeNull()

            appearanceCommands(dialogOpen = false, appearance).single().run()

            appearance.state.value.shouldNotBeNull()
        }

    @Test
    fun `the texts of the menu item and the command exist`() {
        Strings.keys shouldContain "command.appearance"
        Strings.keys shouldContain "menu.appearance"
        Strings["command.appearance"].shouldNotBeBlank()
        Strings["menu.appearance"] shouldBe "Wygląd…"
    }
}
