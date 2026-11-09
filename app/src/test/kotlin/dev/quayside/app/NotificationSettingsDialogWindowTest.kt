package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import dev.quayside.notifications.api.VoiceSample
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime

/**
 * The notification settings dialog in the whole window on fakes (FR-N1, AM-704). The project `Quayside` has two
 * sessions; the repo is a [FakeNotificationSettingsRepo].
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsDialogWindowTest {
    private companion object {
        const val WAIT_MS = 5_000L
    }

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

    private val voices =
        FakeVoiceCatalog(
            listOf(
                VoiceOption(VoiceId("Zosia"), "pl_PL", "Witaj, nazywam się Zosia."),
                VoiceOption(VoiceId("Alex"), "en_US", "Hi, I am Alex."),
            ),
        )

    private val preview = FakeVoicePreview()

    private fun ComposeUiTest.window(): CommandPaletteController =
        showWindow(
            FakeProjectsApi(initial = listOf(project)),
            sessions(),
            settings = SettingsRepos(notifications = repo, voices = voices).also { it.preview = preview },
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
            // Given the open dialog; TURN_ENDED uses the popup and the sound by default
            val palette = window()
            openSettings(palette)
            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe
                setOf(NotificationChannel.POPUP, NotificationChannel.SOUND)

            // When the user switches the voice channel of TURN_ENDED on and saves
            await(WindowTags.notificationCell(NotificationKind.TURN_ENDED, NotificationChannel.VOICE))
                .performScrollTo()
                .performClick()
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the new channels at once
            repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe
                setOf(NotificationChannel.POPUP, NotificationChannel.SOUND, NotificationChannel.VOICE)
        }

    @Test
    fun `FR-N3 saved popup position reaches the repo`() =
        runComposeUiTest {
            // Given the open dialog; the popup sits at the bottom right by default
            val palette = window()
            openSettings(palette)
            repo.settings.value.popup.position shouldBe PopupPosition.BOTTOM_RIGHT

            // When the user opens the position list, picks the top left (the first place) and saves
            await(WindowTags.NOTIFICATION_POPUP_POSITION).performScrollTo().performClick()
            await(AmDropdownTags.option(PopupPosition.TOP_LEFT.ordinal)).performClick()
            waitUntilDoesNotExist(hasTestTag(AmDropdownTags.LIST))
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the new position at once
            repo.settings.value.popup.position shouldBe PopupPosition.TOP_LEFT
        }

    @Test
    fun `FR-N4 saved voice and rate reach the repo`() =
        runComposeUiTest {
            // Given the open dialog; the saved voice is Zosia at 190 words per minute, and the list is loaded
            val palette = window()
            openSettings(palette)
            waitUntilExactlyOneExists(
                hasTestTag(WindowTags.NOTIFICATION_SPEECH_VOICE) and hasText("Zosia — polski (Polska)"),
            )

            // When the user opens the voice list, picks Alex, types a rate and saves
            await(WindowTags.NOTIFICATION_SPEECH_VOICE).performScrollTo().performClick()
            waitUntilExactlyOneExists(
                hasText("Alex — angielski (Stany Zjednoczone)") and hasAnyAncestor(hasTestTag(AmDropdownTags.LIST)),
            )
            onNode(
                hasText("Alex — angielski (Stany Zjednoczone)") and hasAnyAncestor(hasTestTag(AmDropdownTags.LIST)),
            ).performClick()
            waitUntilDoesNotExist(hasTestTag(AmDropdownTags.LIST))
            await(WindowTags.NOTIFICATION_SPEECH_RATE).performScrollTo().performTextReplacement("250")
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the new voice and rate at once
            repo.settings.value.speech.voice shouldBe VoiceId("Alex")
            repo.settings.value.speech.rate shouldBe 250
        }

    @Test
    fun `FR-N4 preview plays through the wired port`() =
        runComposeUiTest {
            // Given the open dialog with Zosia, rate 190 and volume 70, and the list loaded
            val palette = window()
            openSettings(palette)
            waitUntilExactlyOneExists(
                hasTestTag(WindowTags.NOTIFICATION_SPEECH_VOICE) and hasText("Zosia — polski (Polska)"),
            )

            // When the user clicks "Odsłuchaj"
            await(WindowTags.NOTIFICATION_PREVIEW).performScrollTo().performClick()

            // Then the port got Zosia's example with the drafted rate and volume
            waitUntil(timeoutMillis = WAIT_MS) { preview.played.isNotEmpty() }
            preview.played shouldBe
                listOf(VoiceSample(VoiceId("Zosia"), "Witaj, nazywam się Zosia.", rate = 190, volume = 70))
        }

    @Test
    fun `FR-N4 closing the dialog stops the preview`() =
        runComposeUiTest {
            // Given a sample that plays until stopped
            preview.gate = CompletableDeferred()
            val palette = window()
            openSettings(palette)
            waitUntilExactlyOneExists(
                hasTestTag(WindowTags.NOTIFICATION_SPEECH_VOICE) and hasText("Zosia — polski (Polska)"),
            )
            await(WindowTags.NOTIFICATION_PREVIEW).performScrollTo().performClick()
            waitUntil(timeoutMillis = WAIT_MS) { preview.played.isNotEmpty() }

            // When the user closes the dialog with "Anuluj"
            await(WindowTags.NOTIFICATION_SETTINGS_CANCEL).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the sample is stopped
            waitUntil(timeoutMillis = WAIT_MS) { preview.cancelled == 1 }
        }

    @Test
    fun `FR-N6 saved quiet hours reach the repo`() =
        runComposeUiTest {
            // Given the open dialog; the quiet hours are off by default
            val palette = window()
            openSettings(palette)
            repo.settings.value.quietHours shouldBe QuietHours()

            // When the user switches them on, types 23:00 and 06:30 and saves
            await(WindowTags.NOTIFICATION_QUIET_ENABLED).performScrollTo().performClick()
            await(WindowTags.NOTIFICATION_QUIET_FROM).performScrollTo().performTextReplacement("23:00")
            await(WindowTags.NOTIFICATION_QUIET_TO).performScrollTo().performTextReplacement("06:30")
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the hours at once
            repo.settings.value.quietHours shouldBe
                QuietHours(enabled = true, from = LocalTime.of(23, 0), to = LocalTime.of(6, 30))
        }

    @Test
    fun `FR-N7 saved project profile reaches the repo`() =
        runComposeUiTest {
            // Given the open dialog; the project Quayside has no exception
            val palette = window()
            openSettings(palette)
            repo.settings.value.projects shouldBe emptyMap()

            // When the user opens the profile list of Quayside, picks "Wyciszony" and saves
            await(WindowTags.notificationProjectProfile(project.id.value)).performScrollTo().performClick()
            await(AmDropdownTags.option(ProjectProfile.MUTED.ordinal)).performClick()
            waitUntilDoesNotExist(hasTestTag(AmDropdownTags.LIST))
            await(WindowTags.NOTIFICATION_SETTINGS_SAVE).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.NOTIFICATION_SETTINGS_DIALOG))

            // Then the repo holds the exception under the id of the project
            repo.settings.value.projects shouldBe mapOf(project.id to ProjectNotifications(ProjectProfile.MUTED))
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
