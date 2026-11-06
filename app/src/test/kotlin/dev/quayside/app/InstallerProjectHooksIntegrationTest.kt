package dev.quayside.app

import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.agentruntime.impl.hookInstallerModule
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.ui.ProjectHooksError
import dev.quayside.projects.ui.ProjectHooksState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * [InstallerProjectHooks] over the real installer of `agent-runtime:impl` on a temporary home, backup folder and
 * project folders (AM-610). Never the real `~` or a real project (NFR-5).
 */
@Suppress("InjectDispatcher")
class InstallerProjectHooksIntegrationTest {
    @TempDir
    lateinit var root: Path

    private val project get() = root.resolve("project")
    private val localSettings get() = project.resolve(".claude/settings.local.json")
    private val sharedSettings get() = project.resolve(".claude/settings.json")
    private val foreign =
        """{"model": "opus", "hooks": {"Stop": [{"hooks": [{"type": "command", "command": "x.sh"}]}]}}"""

    private fun installer(): HookInstaller =
        hookInstallerModule(
            root.resolve("home"),
            root.resolve("hooks-backup"),
            TestClock(),
            Dispatchers.IO,
            NoOpLogger,
            port = HOOK_PORT,
        )

    private fun hooks(installer: HookInstaller) = InstallerProjectHooks(installer, Dispatchers.IO)

    @Test
    fun `project with installed hooks is present`() =
        runTest {
            project.createDirectories()
            val installer = installer()
            installer.install(HookScope.Project(project))

            hooks(installer).status(project) shouldBe ProjectHooksState.PRESENT
        }

    @Test
    fun `project without hooks is absent and no file appears`() =
        runTest {
            project.createDirectories()

            hooks(installer()).status(project) shouldBe ProjectHooksState.ABSENT

            localSettings.exists() shouldBe false
        }

    @Test
    fun `unreadable settings are unknown`() =
        runTest {
            localSettings.parent.createDirectories()
            localSettings.writeText("{ not json")

            hooks(installer()).status(project) shouldBe ProjectHooksState.UNKNOWN
        }

    @Test
    fun `uninstall removes only our hooks and keeps foreign settings`() =
        runTest {
            localSettings.parent.createDirectories()
            localSettings.writeText(foreign)
            sharedSettings.writeText(foreign)
            val installer = installer()
            installer.install(HookScope.Project(project))

            hooks(installer).uninstall(project) shouldBe Outcome.Ok(Unit)

            localSettings.readText() shouldNotContain "127.0.0.1"
            localSettings.readText() shouldContain "x.sh"
            localSettings.readText() shouldContain "\"model\": \"opus\""
            sharedSettings.readText() shouldBe foreign
            installer.status(HookScope.Project(project)) shouldBe HookStatus.NotInstalled
            hooks(installer).status(project) shouldBe ProjectHooksState.ABSENT
        }

    @Test
    fun `uninstall leaves the global settings alone`() =
        runTest {
            project.createDirectories()
            val installer = installer()
            installer.install(HookScope.Global)
            installer.install(HookScope.Project(project))

            hooks(installer).uninstall(project) shouldBe Outcome.Ok(Unit)

            installer.status(HookScope.Global) shouldBe HookStatus.Installed
        }

    @Test
    fun `missing folder is not recreated by status or uninstall`() =
        runTest {
            val hooks = hooks(installer())

            hooks.status(project) shouldBe ProjectHooksState.ABSENT
            hooks.uninstall(project) shouldBe Outcome.Err(ProjectHooksError.WRITE_FAILED)

            Files.exists(project) shouldBe false
        }
}
