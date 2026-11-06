package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The "Hooki Claude Code" dialog with project rows in the whole window on fakes (NFR-5, NFR-6, AM-609). The window
 * gets `ProjectHookTargets` over the fake projects; no folder is read from disk (the folder check is a lambda).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("InjectDispatcher")
class HookSetupProjectsWindowTest {
    @TempDir
    lateinit var dir: Path

    private val hooks = FakeHookInstaller()
    private val offer by lazy { hookOfferStore(dir) }
    private val alphaDir = Path.of("/work/alpha")
    private val betaDir = Path.of("/work/beta")
    private val goneDir = Path.of("/work/gone")
    private val projects =
        FakeProjectsApi(
            listOf(
                Project(ProjectId("p1"), alphaDir, "alpha"),
                Project(ProjectId("p2"), betaDir, "beta"),
                Project(ProjectId("p3"), goneDir, "gone"),
            ),
        )

    private fun ComposeUiTest.openHooks(withMissing: Boolean = false) {
        val repos = SettingsRepos(hooks = hooks, hookOffer = offer)
        repos.hookTargets =
            ProjectHookTargets(projects.projects, Dispatchers.Unconfined) { withMissing.not() || it != goneDir }
        val palette: CommandPaletteController = showWindow(projects, settings = repos)
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.hookSetup"])
        await(WindowTags.paletteRow("hooks.setup")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
        await(HookTags.DIALOG)
    }

    private fun ComposeUiTest.awaitRow(
        index: Int,
        text: String,
    ) {
        waitUntilExactlyOneExists(hasTestTag(HookTags.row(index)) and hasText(text, substring = true), WAIT_MS)
    }

    private fun ComposeUiTest.awaitStatus(text: String) {
        waitUntilExactlyOneExists(hasTestTag(HookTags.STATUS) and hasText(text, substring = true), WAIT_MS)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    private fun ComposeUiTest.awaitAllRead() {
        awaitRow(1, Strings["hooks.status.installed"])
        awaitRow(2, Strings["hooks.status.notInstalled"])
    }

    @Test
    fun `NFR-5 dialog lists global and every added project with its status`() =
        runComposeUiTest {
            // Given hooks installed in alpha only
            hooks.hasStatus(HookScope.Project(alphaDir), HookStatus.Installed)

            // When the user opens the dialog
            openHooks()

            // Then the rows are Globalnie, alpha, beta, gone, each with its status, and nothing was written
            awaitRow(0, Strings["hooks.row.global"])
            awaitRow(1, "alpha")
            awaitRow(1, Strings["hooks.status.installed"])
            awaitRow(2, "beta")
            awaitRow(2, Strings["hooks.status.notInstalled"])
            awaitRow(3, "gone")
            hooks.calls.none { it.startsWith("install") || it.startsWith("uninstall") } shouldBe true
        }

    @Test
    fun `NFR-5 install in a selected project changes only that project`() =
        runComposeUiTest {
            // Given the open dialog and the project beta selected
            openHooks()
            awaitRow(2, Strings["hooks.status.notInstalled"])
            onNodeWithTag(HookTags.row(2)).performClick()
            awaitStatus(Strings["hooks.status.notInstalled"])

            // When the user clicks "Zainstaluj"
            onNodeWithTag(HookTags.INSTALL).performClick()

            // Then only beta is installed, its row shows it, and the focus stays on "Zamknij"
            awaitRow(2, Strings["hooks.status.installed"])
            awaitFocus(HookTags.CLOSE)
            hooks.calls.filter { it.startsWith("install") } shouldBe listOf("install /work/beta")
            onNodeWithTag(HookTags.NOTICE).assertTextContains(WINDOW_HOOK_BACKUP_DIR.toString(), substring = true)
        }

    @Test
    fun `NFR-5 uninstall in a selected project shows not installed`() =
        runComposeUiTest {
            // Given hooks installed in alpha, the project selected
            hooks.hasStatus(HookScope.Project(alphaDir), HookStatus.Installed)
            openHooks()
            awaitAllRead()
            onNodeWithTag(HookTags.row(1)).performClick()
            onNodeWithTag(HookTags.UNINSTALL).assertIsEnabled()

            // When the user clicks "Odinstaluj"
            onNodeWithTag(HookTags.UNINSTALL).performClick()

            // Then alpha is not installed and the global file was not touched
            awaitRow(1, Strings["hooks.status.notInstalled"])
            onNodeWithTag(HookTags.NOTICE).assertTextContains(Strings["hooks.notice.uninstalled"])
            hooks.calls.filter { it.startsWith("uninstall") } shouldBe listOf("uninstall /work/alpha")
        }

    @Test
    fun `NFR-5 selecting a row with Space changes nothing`() = selectWithKey(Key.Spacebar)

    @Test
    fun `NFR-5 selecting a row with Enter changes nothing`() = selectWithKey(Key.Enter)

    private fun selectWithKey(key: Key) =
        runComposeUiTest {
            // Given the open dialog with every status read
            hooks.hasStatus(HookScope.Project(alphaDir), HookStatus.Installed)
            openHooks()
            awaitAllRead()

            // When the user focuses a row and presses the key
            onNodeWithTag(HookTags.row(2)).performClick()
            onNodeWithTag(HookTags.row(2)).performKeyInput { pressKey(key) }
            waitForIdle()

            // Then only statuses were read
            hooks.calls.all { it.startsWith("status ") } shouldBe true
        }

    @Test
    fun `NFR-5 focus stays on Close after the rows are read`() =
        runComposeUiTest {
            // Given the open dialog
            openHooks()
            awaitFocus(HookTags.CLOSE)

            // When the rows are read
            awaitRow(2, Strings["hooks.status.notInstalled"])

            // Then neither a row nor an install button took the focus
            awaitFocus(HookTags.CLOSE)
            onNodeWithTag(HookTags.CLOSE).assertTextContains(Strings["hooks.close"])
        }

    @Test
    fun `NFR-6 rows are reachable with Tab after the buttons`() =
        runComposeUiTest {
            // Given the open dialog where both actions are possible on "Globalnie"
            hooks.hasStatus(HookScope.Global, HookStatus.Partial(setOf(HookKind.STOP)))
            hooks.hasStatus(HookScope.Project(alphaDir), HookStatus.Installed) // awaitAllRead expects it
            openHooks()
            awaitAllRead()
            awaitFocus(HookTags.CLOSE)

            // When the user presses Tab, once per stop
            val order =
                listOf(HookTags.CLOSE, HookTags.INSTALL, HookTags.UNINSTALL, HookTags.row(0), HookTags.row(1))
            order.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves to the next stop
                awaitFocus(to)
            }
        }

    @Test
    fun `NFR-5 missing project folder shows the notice and disabled buttons`() =
        runComposeUiTest {
            // Given a project whose folder is gone
            openHooks(withMissing = true)
            awaitRow(3, Strings["hooks.status.missingDir"])

            // When the user selects it
            onNodeWithTag(HookTags.row(3)).performClick()

            // Then the text says so, both actions are off, and its status was never read
            awaitStatus(Strings["hooks.status.missingDir"])
            onNodeWithTag(HookTags.INSTALL).assertIsNotEnabled()
            onNodeWithTag(HookTags.UNINSTALL).assertIsNotEnabled()
            hooks.calls.contains("status /work/gone") shouldBe false
            hooks.calls.all { it.startsWith("status ") } shouldBe true
        }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
