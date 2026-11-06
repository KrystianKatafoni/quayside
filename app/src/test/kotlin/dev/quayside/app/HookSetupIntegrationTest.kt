package dev.quayside.app

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.agentruntime.impl.hookInstallerModule
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The real installer of `agent-runtime:impl` with the port of the app, on a temporary home and backup folder.
 * Never the real `~` (NFR-5): [home] is a temp directory.
 */
@Suppress("InjectDispatcher")
class HookSetupIntegrationTest {
    @TempDir
    lateinit var root: Path

    private val home get() = root.resolve("home")
    private val settings get() = home.resolve(".claude/settings.json")
    private val backups get() = root.resolve("hooks-backup")
    private val foreign =
        """{"model": "opus", "hooks": {"Stop": [{"hooks": [{"type": "command", "command": "x.sh"}]}]}}"""

    private val events = listOf("Notification", "Stop", "StopFailure", "SessionStart", "SessionEnd")

    private fun installer(): HookInstaller =
        hookInstallerModule(home, backups, TestClock(), Dispatchers.IO, NoOpLogger, port = HOOK_PORT)

    private fun writeForeign() {
        settings.parent.createDirectories()
        settings.writeText(foreign)
    }

    @Test
    fun `install writes hooks with the app port`() =
        runTest {
            installer().install(HookScope.Global).shouldBeInstanceOf<Outcome.Ok<BackupRef>>()

            val text = settings.readText()
            text shouldContain "http://127.0.0.1:$HOOK_PORT/hooks"
            events.forEach { text shouldContain it }
            installer().status(HookScope.Global) shouldBe HookStatus.Installed
        }

    @Test
    fun `install keeps foreign settings and makes a backup`() =
        runTest {
            writeForeign()

            val ref = installer().install(HookScope.Global).getOrNull().shouldNotBeNull()

            settings.readText() shouldContain "x.sh"
            settings.readText() shouldContain "\"model\": \"opus\""
            backups.resolve(ref.value).readText() shouldBe foreign
        }

    @Test
    fun `uninstall removes only our hooks`() =
        runTest {
            writeForeign()
            val installer = installer()
            installer.install(HookScope.Global)

            installer.uninstall(HookScope.Global, backup = null) shouldBe Outcome.Ok(Unit)

            settings.readText() shouldNotContain "127.0.0.1"
            settings.readText() shouldContain "x.sh"
            installer.status(HookScope.Global) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `controller installs and uninstalls through the real installer`() =
        runTest {
            writeForeign()
            val controller =
                HookSetupController(
                    installer(),
                    hookOfferStore(root.resolve("app")),
                    globalOnlyTargets,
                    backgroundScope,
                    NoOpLogger,
                )
            controller.open()
            controller.state.first { it?.current?.status == HookStatus.NotInstalled }

            controller.install()
            controller.state.first { it?.notice == HookNotice.Installed }
            settings.readText() shouldContain "127.0.0.1:$HOOK_PORT"

            controller.uninstall()
            controller.state.first { it?.notice == HookNotice.Uninstalled }
            settings.readText() shouldNotContain "127.0.0.1"
            settings.readText() shouldContain "x.sh"
        }

    private val project get() = root.resolve("work/alpha")
    private val projectSettings get() = project.resolve(".claude/settings.local.json")
    private val projectScope get() = HookScope.Project(project)

    private fun writeProjectForeign() {
        projectSettings.parent.createDirectories()
        projectSettings.writeText(foreign)
    }

    @Test
    fun `project install writes settings local json with the app port`() =
        runTest {
            project.createDirectories()

            installer().install(projectScope).shouldBeInstanceOf<Outcome.Ok<BackupRef>>()

            val text = projectSettings.readText()
            text shouldContain "http://127.0.0.1:$HOOK_PORT/hooks"
            events.forEach { text shouldContain it }
            installer().status(projectScope) shouldBe HookStatus.Installed
        }

    @Test
    fun `project install keeps foreign settings and makes a backup`() =
        runTest {
            writeProjectForeign()

            val ref = installer().install(projectScope).getOrNull().shouldNotBeNull()

            projectSettings.readText() shouldContain "x.sh"
            projectSettings.readText() shouldContain "\"model\": \"opus\""
            backups.resolve(ref.value).readText() shouldBe foreign
        }

    @Test
    fun `project uninstall removes only our hooks`() =
        runTest {
            writeProjectForeign()
            val installer = installer()
            installer.install(projectScope)

            installer.uninstall(projectScope, backup = null) shouldBe Outcome.Ok(Unit)

            projectSettings.readText() shouldNotContain "127.0.0.1"
            projectSettings.readText() shouldContain "x.sh"
            installer.status(projectScope) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `project install does not touch the global settings`() =
        runTest {
            writeForeign()
            project.createDirectories()

            installer().install(projectScope)

            settings.readText() shouldBe foreign
            installer().status(HookScope.Global) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `controller installs in a project through the real installer and the target list`() =
        runTest {
            project.createDirectories()
            val target = HookTarget.Project(ProjectId("p1"), "alpha", project, present = true)
            val controller =
                HookSetupController(
                    installer(),
                    hookOfferStore(root.resolve("app")),
                    hookTargets(target),
                    backgroundScope,
                    NoOpLogger,
                )
            controller.open()
            controller.state.first { s -> s != null && s.rows.size == 2 && s.rows.all { it.status != null } }
            controller.select(1)

            controller.install()
            controller.state.first { it?.notice == HookNotice.Installed }

            projectSettings.readText() shouldContain "127.0.0.1:$HOOK_PORT"
            settings.toFile().exists() shouldBe false
        }
}
