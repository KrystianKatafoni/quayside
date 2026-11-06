package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Removing a project with hooks in the whole window on fakes (FR-P3, NFR-5, AM-610): the sidebar dialog reads the
 * hooks of the project through [InstallerProjectHooks] over a [FakeHookInstaller]; no folder is read from disk.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("InjectDispatcher")
class ProjectRemoveHooksWindowTest {
    private val installer = FakeHookInstaller()
    private val alphaDir = Path.of("/work/alpha")
    private val betaDir = Path.of("/work/beta")
    private val projects =
        FakeProjectsApi(
            listOf(
                Project(ProjectId("p1"), alphaDir, "alpha"),
                Project(ProjectId("p2"), betaDir, "beta"),
            ),
        )

    private fun ComposeUiTest.showWithProjectHooks() {
        val setup = WindowSetup(projects)
        setup.projectHooks = InstallerProjectHooks(installer, Dispatchers.Unconfined) { true }
        showWindowOf(setup)
    }

    private fun ComposeUiTest.askToRemove(id: String) {
        await(WindowTags.rowMenu(id)).performClick()
        await(WindowTags.ROW_MENU_REMOVE).performClick()
        await(WindowTags.CONFIRM_REMOVE_PROJECT)
    }

    @Test
    fun `FR-P3 removing a project with hooks warns and uninstall touches only that project`() =
        runComposeUiTest {
            // Given hooks installed in alpha and in beta
            installer.hasStatus(HookScope.Project(alphaDir), HookStatus.Installed)
            installer.hasStatus(HookScope.Project(betaDir), HookStatus.Installed)
            showWithProjectHooks()

            // When the user asks to remove alpha
            askToRemove("p1")

            // Then the dialog offers to uninstall; clicking it changes alpha only and the project stays listed
            await(WindowTags.UNINSTALL_PROJECT_HOOKS).assertIsDisplayed().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.UNINSTALL_PROJECT_HOOKS))
            installer.calls.filter { it.startsWith("uninstall") } shouldBe listOf("uninstall /work/alpha")
            installer.calls.none { it.endsWith("global") } shouldBe true
            installer.calls.none { it.startsWith("install") } shouldBe true
            projects.calls shouldBe emptyList()
            onNodeWithTag(WindowTags.CONFIRM_REMOVE_PROJECT).assertIsDisplayed()
        }

    @Test
    fun `FR-P3 removing a project without hooks shows no uninstall button`() =
        runComposeUiTest {
            // Given no hooks anywhere
            showWithProjectHooks()

            // When the user asks to remove alpha
            askToRemove("p1")

            // Then there is no warning button and nothing was uninstalled
            onNodeWithTag(WindowTags.UNINSTALL_PROJECT_HOOKS).assertDoesNotExist()
            installer.calls.none { it.startsWith("uninstall") } shouldBe true
        }
}
