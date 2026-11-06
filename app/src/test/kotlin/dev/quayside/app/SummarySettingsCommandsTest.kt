package dev.quayside.app

import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.summaries.api.FakeSummarySettingsRepo
import dev.quayside.summaries.ui.SummarySettingsController
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The palette command "Ustawienia podsumowań" and its guard against open dialogs (FR-S7, AM-606). */
class SummarySettingsCommandsTest {
    private fun TestScope.settings() = SummarySettingsController(FakeSummarySettingsRepo(), backgroundScope)

    @Test
    fun `offers summary settings when no dialog is open`() =
        runTest {
            val command = summarySettingsCommands(dialogOpen = false, settings()).single()

            command.id shouldBe CommandId("summaries.settings")
            command.title shouldBe Strings["command.summarySettings"]
            command.group shouldBe CommandGroup.APP
        }

    @Test
    fun `hidden while another dialog is open`() =
        runTest {
            summarySettingsCommands(dialogOpen = true, settings()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the dialog`() =
        runTest {
            val settings = settings()
            settings.state.value.shouldBeNull()

            summarySettingsCommands(dialogOpen = false, settings).single().run()

            settings.state.value.shouldNotBeNull()
        }
}
