package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.summaries.api.FakeSummarySettingsRepo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The summaries settings dialog in the whole window on a fake repo (FR-S7, AM-606). */
@OptIn(ExperimentalTestApi::class)
class SummarySettingsDialogWindowTest {
    @TempDir
    lateinit var root: Path

    private val repo = FakeSummarySettingsRepo()

    private fun ComposeUiTest.window(): CommandPaletteController {
        val project = aProject(id = "project0001", dir = Files.createDirectory(root.resolve("quayside")))
        val settings = SettingsRepos().also { it.summaries = repo }
        return showWindow(FakeProjectsApi(initial = listOf(project)), settings = settings)
    }

    private fun ComposeUiTest.openSettings(palette: CommandPaletteController) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.summarySettings"])
        await(WindowTags.paletteRow("summaries.settings")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
    }

    @Test
    fun `FR-S7 palette opens the summaries settings`() =
        runComposeUiTest {
            // Given the window and the closed palette
            val palette = window()
            onNodeWithTag(WindowTags.SUMMARY_SETTINGS_DIALOG).assertDoesNotExist()

            // When the user picks "Ustawienia podsumowań"
            openSettings(palette)

            // Then the dialog shows and the palette is gone
            await(WindowTags.SUMMARY_SETTINGS_DIALOG)
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }

    @Test
    fun `FR-S7 saving the switch reaches the repo and asks for a restart`() =
        runComposeUiTest {
            // Given the open dialog with the model off
            val palette = window()
            openSettings(palette)
            repo.useModel.value shouldBe false

            // When the user switches it on
            await(WindowTags.SUMMARY_USE_MODEL).performClick()

            // Then the dialog says the change waits for a restart, and nothing is saved yet
            await(WindowTags.SUMMARY_RESTART_NOTE)
            repo.useModel.value shouldBe false

            // When the user saves
            await(WindowTags.SUMMARY_SETTINGS_SAVE).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.SUMMARY_SETTINGS_DIALOG))

            // Then the repo holds the switch
            repo.useModel.value shouldBe true
        }
}
