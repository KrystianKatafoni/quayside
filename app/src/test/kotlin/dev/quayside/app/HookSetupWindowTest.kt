package dev.quayside.app

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.FakeProjectsApi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The "Hooki Claude Code" dialog in the whole window on fakes (NFR-5, NFR-6, AM-607). The file `hook-offer.json`
 * lives in a temp directory. The dialog is a focusable popup, so keys reach its buttons and not the window.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("ForbiddenMethodCall") // runBlocking is allowed in tests (ENG-C3).
class HookSetupWindowTest {
    @TempDir
    lateinit var dir: Path

    private val hooks = FakeHookInstaller()
    private val offer by lazy { hookOfferStore(dir) }
    private val global = HookScope.Global

    private fun ComposeUiTest.window(atStart: Boolean = false): CommandPaletteController =
        showWindow(
            FakeProjectsApi(),
            settings = SettingsRepos(hooks = hooks, hookOffer = offer, hookOfferAtStart = atStart),
        )

    private fun ComposeUiTest.openHooks(palette: CommandPaletteController) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.hookSetup"])
        await(WindowTags.paletteRow("hooks.setup")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
        await(HookTags.DIALOG)
    }

    private fun ComposeUiTest.awaitStatus(text: String) {
        waitUntilExactlyOneExists(hasTestTag(HookTags.STATUS) and hasText(text, substring = true), WAIT_MS)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    @Test
    fun `NFR-5 palette command opens the hooks dialog with the status`() =
        runComposeUiTest {
            // Given hooks installed and the closed palette
            hooks.hasStatus(global, HookStatus.Installed)
            val palette = window()
            onNodeWithTag(HookTags.DIALOG).assertDoesNotExist()

            // When the user picks "Hooki Claude Code" in the palette
            openHooks(palette)

            // Then the dialog shows the status, the plain close button, and only the status was read
            awaitStatus(Strings["hooks.status.installed"])
            awaitFocus(HookTags.CLOSE)
            onNodeWithTag(HookTags.CLOSE).assertTextContains(Strings["hooks.close"])
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `NFR-5 dialog says what changes before the install`() =
        runComposeUiTest {
            // Given the open dialog on missing hooks
            openHooks(window())

            // Then the text names the file, the events, the address, the backup folder and the outside sessions
            val info = onNodeWithTag(HookTags.INFO)
            listOf(
                "~/.claude/settings.json",
                "Notification",
                "StopFailure",
                "SessionEnd",
                "http://127.0.0.1:$HOOK_PORT/hooks",
                WINDOW_HOOK_BACKUP_DIR.toString(),
                "poza Quayside",
            ).forEach { info.assertTextContains(it, substring = true) }
            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `NFR-5 install from the dialog shows the backup folder and installed status`() =
        runComposeUiTest {
            // Given the open dialog on missing hooks
            openHooks(window())
            awaitStatus(Strings["hooks.status.notInstalled"])
            onNodeWithTag(HookTags.INSTALL).assertIsEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsNotEnabled()

            // When the user clicks "Zainstaluj"
            onNodeWithTag(HookTags.INSTALL).performClick()

            // Then the notice names the backup folder and the status is installed
            waitUntilExactlyOneExists(
                hasTestTag(HookTags.NOTICE) and hasText(WINDOW_HOOK_BACKUP_DIR.toString(), substring = true),
                WAIT_MS,
            )
            awaitStatus(Strings["hooks.status.installed"])
            onNodeWithTag(HookTags.INSTALL).assertIsNotEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsEnabled()
            awaitFocus(HookTags.CLOSE) // the focus never jumps to the opposite action
            hooks.calls shouldBe listOf("status global", "install global", "status global")
        }

    @Test
    fun `NFR-5 uninstall from the dialog shows not installed`() =
        runComposeUiTest {
            // Given the open dialog on installed hooks
            hooks.hasStatus(global, HookStatus.Installed)
            openHooks(window())
            awaitStatus(Strings["hooks.status.installed"])

            // When the user clicks "Odinstaluj"
            onNodeWithTag(HookTags.UNINSTALL).performClick()

            // Then the status is not installed, and the install is possible again
            awaitStatus(Strings["hooks.status.notInstalled"])
            onNodeWithTag(HookTags.NOTICE).assertTextContains(Strings["hooks.notice.uninstalled"])
            onNodeWithTag(HookTags.INSTALL).assertIsEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsNotEnabled()
            awaitFocus(HookTags.CLOSE)
            hooks.calls shouldBe listOf("status global", "uninstall global", "status global")
        }

    @Test
    fun `NFR-5 unreadable settings disable both actions`() =
        runComposeUiTest {
            // Given a settings file that cannot be read
            hooks.hasStatus(global, HookStatus.Unreadable("json:invalid"))

            // When the user opens the dialog
            openHooks(window())

            // Then the status says so and neither action is possible
            awaitStatus(Strings["hooks.status.unreadable"])
            onNodeWithTag(HookTags.INSTALL).assertIsNotEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsNotEnabled()
        }

    @Test
    fun `NFR-5 partial status lists the missing events`() =
        runComposeUiTest {
            // Given only StopFailure missing
            hooks.hasStatus(global, HookStatus.Partial(setOf(HookKind.STOP_FAILURE)))

            // When the user opens the dialog
            openHooks(window())

            // Then the status names it and both actions are possible
            awaitStatus("StopFailure")
            onNodeWithTag(HookTags.INSTALL).assertIsEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsEnabled()
        }

    @Test
    fun `NFR-5 failed install shows the file and not the reason`() =
        runComposeUiTest {
            // Given an install that fails to write the file
            val file = Path.of("/home/test/.claude/settings.json")
            hooks.failNext(HookError.WriteFailed(file, "secret-reason"))
            openHooks(window())
            awaitStatus(Strings["hooks.status.notInstalled"])

            // When the user clicks "Zainstaluj"
            onNodeWithTag(HookTags.INSTALL).performClick()

            // Then the notice names the file, never the reason, and the dialog stays
            val notice = hasTestTag(HookTags.NOTICE) and hasText(file.toString(), substring = true)
            waitUntilExactlyOneExists(notice, WAIT_MS)
            onAllNodes(hasText("secret-reason", substring = true)).assertCountEquals(0)
            onNodeWithTag(HookTags.DIALOG).assertExists()
            onNodeWithTag(HookTags.INSTALL).assertIsEnabled()
        }

    @Test
    fun `NFR-6 failed install notice is drawn in the err token`() =
        runComposeUiTest {
            // Given an install that fails, in the real plex theme
            val err =
                appearanceModule()
                    .catalog
                    .lookOf(AppearanceSettings())
                    .colors.status.err
            hooks.failNext(HookError.WriteFailed(Path.of("/home/test/.claude/settings.json"), "reason"))
            openHooks(window())
            awaitStatus(Strings["hooks.status.notInstalled"])

            // When the user clicks "Zainstaluj" and the failure notice shows
            onNodeWithTag(HookTags.INSTALL).performClick()
            waitUntilExactlyOneExists(hasTestTag(HookTags.NOTICE), WAIT_MS)

            // Then the notice text has the colour err
            val image = onNodeWithTag(HookTags.NOTICE).captureToImage().toPixelMap()
            val drawn =
                (0 until image.width).flatMapTo(
                    HashSet(),
                ) { x -> (0 until image.height).map { image[x, it].toArgb() } }
            drawn shouldContain err.toColor().toArgb()
        }

    @Test
    fun `NFR-5 escape closes without changes`() =
        runComposeUiTest {
            // Given the open dialog on missing hooks
            openHooks(window())
            awaitFocus(HookTags.CLOSE)

            // When the user presses Escape
            onNodeWithTag(HookTags.CLOSE).performKeyInput { pressKey(Key.Escape) }

            // Then the dialog is gone, the file untouched and the choice not remembered
            waitUntilDoesNotExist(hasTestTag(HookTags.DIALOG))
            hooks.calls shouldBe listOf("status global")
            runBlocking { offer.dismissed() } shouldBe false
        }

    @Test
    fun `NFR-6 dialog buttons are reachable with Tab`() =
        runComposeUiTest {
            // Given the open dialog where install and uninstall are both possible
            hooks.hasStatus(global, HookStatus.Partial(setOf(HookKind.STOP)))
            openHooks(window())
            awaitStatus("Stop")

            // Then the focus starts on the harmless button: Enter right after opening must not change the file
            awaitFocus(HookTags.CLOSE)

            // When the user presses Tab, once per button; the order is: close, install, uninstall
            listOf(HookTags.CLOSE, HookTags.INSTALL, HookTags.UNINSTALL).zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next button
                awaitFocus(to)
            }

            // When the user presses Shift Tab on the last button
            onNodeWithTag(HookTags.UNINSTALL).performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }

            // Then the focus goes back one button
            awaitFocus(HookTags.INSTALL)
        }

    @Test
    fun `NFR-6 disabled button is skipped by Tab`() =
        runComposeUiTest {
            // Given the open dialog on installed hooks: install is disabled
            hooks.hasStatus(global, HookStatus.Installed)
            openHooks(window())
            awaitStatus(Strings["hooks.status.installed"])
            awaitFocus(HookTags.CLOSE)

            // When the user presses Tab
            onNodeWithTag(HookTags.CLOSE).performKeyInput { pressKey(Key.Tab) }

            // Then the focus goes to uninstall
            awaitFocus(HookTags.UNINSTALL)
        }

    @Test
    fun `NFR-5 first start offers hooks when they are missing`() =
        runComposeUiTest {
            // Given missing hooks and no remembered choice
            // When the window starts
            window(atStart = true)

            // Then the dialog shows by itself, with "Pomiń" instead of "Zamknij"
            await(HookTags.DIALOG)
            awaitStatus(Strings["hooks.status.notInstalled"])
            onNodeWithTag(HookTags.CLOSE).assertTextContains(Strings["hooks.skip"])
            awaitFocus(HookTags.CLOSE)
            onNodeWithTag(HookTags.INSTALL).assertIsEnabled()
        }

    @Test
    fun `NFR-5 Enter right after the start offer skips and does not install`() = keyAfterOffer(Key.Enter)

    @Test
    fun `NFR-5 Space right after the start offer skips and does not install`() = keyAfterOffer(Key.Spacebar)

    private fun keyAfterOffer(key: Key) =
        runComposeUiTest {
            // Given the start offer, the moment it shows (the status may not be read yet)
            window(atStart = true)
            await(HookTags.DIALOG)
            awaitFocus(HookTags.CLOSE)

            // When the user presses the key at once
            onNodeWithTag(HookTags.CLOSE).performKeyInput { pressKey(key) }

            // Then the dialog closes as "Pomiń": nothing installed, the choice remembered
            waitUntilDoesNotExist(hasTestTag(HookTags.DIALOG))
            hooks.calls shouldBe listOf("status global")
            waitUntil(timeoutMillis = WAIT_MS) { runBlocking { offer.dismissed() } }
        }

    @Test
    fun `NFR-5 nothing changes without consent`() =
        runComposeUiTest {
            // Given the start offer
            window(atStart = true)
            await(HookTags.DIALOG)
            awaitStatus(Strings["hooks.status.notInstalled"])

            // When the user clicks "Pomiń"
            onNodeWithTag(HookTags.CLOSE).performClick()

            // Then the dialog is gone, only the status was read and the choice is remembered
            waitUntilDoesNotExist(hasTestTag(HookTags.DIALOG))
            hooks.calls shouldBe listOf("status global")
            waitUntil(timeoutMillis = WAIT_MS) { runBlocking { offer.dismissed() } }
        }

    @Test
    fun `NFR-5 no offer after skip`() =
        runComposeUiTest {
            // Given a remembered choice (the user skipped before)
            runBlocking { offer.dismiss() }

            // When the window starts
            val palette = window(atStart = true)
            waitUntil(timeoutMillis = WAIT_MS) { hooks.calls.isNotEmpty() }

            // Then no dialog shows; the palette still opens it, with the plain close button
            onNodeWithTag(HookTags.DIALOG).assertDoesNotExist()
            openHooks(palette)
            onNodeWithTag(HookTags.CLOSE).assertTextContains(Strings["hooks.close"])
        }

    @Test
    fun `NFR-5 no offer when hooks are installed`() =
        runComposeUiTest {
            // Given installed hooks and no remembered choice
            hooks.hasStatus(global, HookStatus.Installed)

            // When the window starts
            window(atStart = true)
            waitUntil(timeoutMillis = WAIT_MS) { hooks.calls.isNotEmpty() }
            waitForIdle()

            // Then no dialog shows
            onNodeWithTag(HookTags.DIALOG).assertDoesNotExist()
            hooks.calls shouldBe listOf("status global")
        }

    /**
     * Keys go to the dialog because its popup takes the focus (a focusable `Popup`), not because the commands
     * check for it; the guard of the commands is tested in [HookSetupCommandsTest].
     */
    @Test
    fun `NFR-5 window shortcuts do nothing while the dialog is open`() =
        runComposeUiTest {
            // Given the open dialog
            openHooks(window())

            // When the user presses g and p (the project switch chord)
            listOf(Key.G, Key.P).forEach { key ->
                onNodeWithTag(WindowTags.KEYS).performKeyInput {
                    keyDown(key)
                    keyUp(key)
                }
                waitForIdle()
            }

            // Then no palette opened and the dialog is still open
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            onNodeWithTag(HookTags.DIALOG).assertExists()
        }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
