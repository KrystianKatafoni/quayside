package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The notification settings dialog in the whole window on fakes (FR-N1, AM-704). The project `Quayside` has two
 * sessions; the repo is a [FakeNotificationSettingsRepo].
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsDialogWindowTest {
    @TempDir
    lateinit var root: Path

    private val repo = FakeNotificationSettingsRepo()
    private val projectDir by lazy { Files.createDirectory(root.resolve("quayside")) }
    private val project by lazy { aProject(id = "project0001", dir = projectDir, displayName = "Quayside") }

    private fun session(
        name: String,
        startedAtSecond: Long,
    ): WorldSession =
        WorldSession(
            id = SessionId(name),
            cwd = projectDir,
            state = SessionState.Working,
            name = name,
            startedAt = T0.plusSeconds(startedAtSecond),
        )

    private fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(project.id, project.dir)),
                listOf(session("older-work", 0), session("newer-work", 60)),
            ),
        )

    private fun ComposeUiTest.window(): CommandPaletteController =
        showWindow(
            FakeProjectsApi(initial = listOf(project)),
            sessions(),
            settings = SettingsRepos(notifications = repo),
        )

    private fun ComposeUiTest.openSettings(palette: CommandPaletteController) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.notificationSettings"])
        await(WindowTags.paletteRow("notifications.settings")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
    }

    @Test
    fun `FR-N1 palette opens notification settings`() =
        runComposeUiTest {
            // Given the window with the default settings and the closed palette
            val palette = window()
            onNodeWithTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG).assertDoesNotExist()

            // When the user picks "Ustawienia powiadomień" in the palette
            openSettings(palette)

            // Then the dialog shows and the palette is gone
            await(WindowTags.NOTIFICATION_SETTINGS_DIALOG)
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }

    @Test
    fun `FR-N1 saved channel change reaches the repo`() =
        runComposeUiTest {
            // Given the open dialog; TURN_ENDED uses only the popup by default
            val palette = window()
            openSettings(palette)
            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(NotificationChannel.POPUP)

            // When the user switches the sound channel of TURN_ENDED on and saves
            await(WindowTags.notificationCell(NotificationKind.TURN_ENDED, NotificationChannel.SOUND))
                .performScrollTo()
                .performClick()
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the new channels at once
            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe
                setOf(NotificationChannel.POPUP, NotificationChannel.SOUND)
        }

    /**
     * Keys go to the dialog because its popup takes the focus (a focusable `Popup`), not because the commands
     * check for it; the guard of the commands is tested in [NotificationSettingsCommandsTest].
     */
    @Test
    fun `FR-N1 window shortcuts do nothing while the dialog is open`() =
        runComposeUiTest {
            // Given a selected project with sessions and the dialog open
            val palette = window()
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)
            openSettings(palette)
            await(WindowTags.NOTIFICATION_SETTINGS_DIALOG)

            // When the user presses j and then g p
            listOf(Key.J, Key.G, Key.P).forEach { key ->
                onNodeWithTag(WindowTags.KEYS).performKeyInput {
                    keyDown(key)
                    keyUp(key)
                }
                waitForIdle()
            }

            // Then no session is selected, no palette opened and the dialog is still open
            onNodeWithTag(WindowTags.DETAILS_EMPTY).assertExists()
            onNodeWithTag(WindowTags.DETAILS).assertDoesNotExist()
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            onNodeWithTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG).assertExists()
        }
}
