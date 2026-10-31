package dev.agentmanager.agentruntime.api

import dev.agentmanager.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeHookInstallerTest {
    private val project = HookScope.Project(A_PROJECT_DIR)
    private val writeFailed = HookError.WriteFailed(A_PROJECT_DIR.resolve(".claude/settings.local.json"), "io")

    @Test
    fun `failNext affects only the next install`() =
        runTest {
            val installer = FakeHookInstaller()

            installer.failNext(writeFailed)

            installer.install(project) shouldBe Outcome.Err(writeFailed)
            installer.status(project) shouldBe HookStatus.NotInstalled
            installer.install(project) shouldBe Outcome.Ok(BackupRef("backup0001"))
        }

    @Test
    fun `failNext applies to uninstall and keeps the hooks`() =
        runTest {
            val installer = FakeHookInstaller()
            installer.install(HookScope.Global)

            installer.failNext(writeFailed)

            installer.uninstall(HookScope.Global, backup = null) shouldBe Outcome.Err(writeFailed)
            installer.status(HookScope.Global) shouldBe HookStatus.Installed
        }

    @Test
    fun `status does not consume failNext`() =
        runTest {
            val installer = FakeHookInstaller()

            installer.failNext(writeFailed)
            installer.status(HookScope.Global)

            installer.install(HookScope.Global) shouldBe Outcome.Err(writeFailed)
        }

    @Test
    fun `backups get deterministic refs in order`() =
        runTest {
            val installer = FakeHookInstaller()

            val first = installer.install(HookScope.Global)
            val second = installer.install(project)

            first shouldBe Outcome.Ok(BackupRef("backup0001"))
            second shouldBe Outcome.Ok(BackupRef("backup0002"))
        }

    @Test
    fun `hasStatus sets the reported status of one scope only`() =
        runTest {
            val installer = FakeHookInstaller()
            val partial = HookStatus.Partial(missing = setOf(HookKind.STOP_FAILURE))

            installer.hasStatus(HookScope.Global, partial)

            installer.status(HookScope.Global) shouldBe partial
            installer.status(project) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `calls are recorded in order`() =
        runTest {
            val installer = FakeHookInstaller()

            installer.status(HookScope.Global)
            installer.install(project)
            installer.uninstall(HookScope.Global, backup = null)

            installer.calls shouldBe
                listOf(
                    "status global",
                    "install $A_PROJECT_DIR",
                    "uninstall global",
                )
        }
}
