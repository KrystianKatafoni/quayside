package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldPastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/**
 * The window "Stare sesje z dysku" from the palette (FR-P9, AM-312): a choice saves at once and the list follows
 * without adding the project again. The host of the dialog is part of `WindowWorld`, like the other settings dialogs.
 */
@OptIn(ExperimentalTestApi::class)
class HistorySettingsWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val recent = SessionId("00000000-0000-4000-8000-000000000001")
    private val old = SessionId("00000000-0000-4000-8000-000000000002")

    private fun fake() =
        world.fake(
            past =
                listOf(
                    WorldPastSession(recent, world.plantDir, T0.minus(Duration.ofDays(1)), "recent chat"),
                    WorldPastSession(old, world.plantDir, T0.minus(Duration.ofDays(10)), "old chat"),
                ),
        )

    private fun androidx.compose.ui.test.ComposeUiTest.openDialog(
        palette: dev.quayside.commandpalette.ui.CommandPaletteController,
    ) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput("Stare sesje")
        await(WindowTags.paletteRow(WindowTags.HISTORY_COMMAND)).performClick()
        await(WindowTags.HISTORY_DIALOG)
    }

    @Test
    fun `FR-P9 changing the age limit changes the list without re-adding the project`() =
        runComposeUiTest {
            // Given a project with a recent and a 10-day-old session, both listed (the default is 30 days)
            val fake = fake()
            val palette = showWindow(world.projects, fake)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(old.value))

            // When the user opens the window and picks 7 days
            openDialog(palette)
            onNodeWithTag(WindowTags.historyAge(HistoryAge.DAYS_7.name)).performClick()

            // Then the old session leaves the list at once and the setting is saved
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow(old.value)))
            onNodeWithTag(WindowTags.missionRow(recent.value)).assertExists()
            fake.historySettings.settings.value.maxAge shouldBe HistoryAge.DAYS_7

            // And without a limit it comes back
            onNodeWithTag(WindowTags.historyAge(HistoryAge.UNLIMITED.name)).performClick()
            waitUntilExactlyOneExists(hasTestTag(WindowTags.missionRow(old.value)))
        }

    @Test
    fun `FR-P9 count limit choice is saved at once`() =
        runComposeUiTest {
            val fake = fake()
            val palette = showWindow(world.projects, fake)
            openDialog(palette)

            onNodeWithTag(WindowTags.historyLimit(HistoryLimit.SESSIONS_1000.name)).performClick()

            waitUntil { fake.historySettings.settings.value.maxPerProject == HistoryLimit.SESSIONS_1000 }
        }
}
