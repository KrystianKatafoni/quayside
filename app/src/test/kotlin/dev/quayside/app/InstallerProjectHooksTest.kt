@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.Outcome
import dev.quayside.projects.ui.ProjectHooksError
import dev.quayside.projects.ui.ProjectHooksState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

/**
 * The adapter of the remove dialog over a [HookInstaller] (AM-610): the mapping of statuses and errors, the rule that
 * a missing project folder never reaches the installer (its write would create the folder), the scope and `backup`.
 */
class InstallerProjectHooksTest {
    private val dir = DIR

    @ParameterizedTest(name = "folder={0}, {1} -> {2}, installer called={3}")
    @MethodSource("statusRows")
    fun `status maps the installer status`(
        folderExists: Boolean,
        status: HookStatus,
        expected: ProjectHooksState,
        called: Boolean,
    ) = runTest {
        val installer = FakeHookInstaller().also { it.hasStatus(HookScope.Project(dir), status) }
        val hooks = InstallerProjectHooks(installer, UnconfinedTestDispatcher(testScheduler)) { folderExists }

        hooks.status(dir) shouldBe expected

        installer.calls shouldBe if (called) listOf("status $dir") else emptyList()
    }

    @ParameterizedTest(name = "folder={0}, {1} -> {2}, installer called={3}")
    @MethodSource("uninstallRows")
    fun `uninstall maps the installer result`(
        folderExists: Boolean,
        failure: HookError?,
        expected: Outcome<Unit, ProjectHooksError>,
        called: Boolean,
    ) = runTest {
        val installer = FakeHookInstaller().also { fake -> failure?.let { fake.failNext(it) } }
        val hooks = InstallerProjectHooks(installer, UnconfinedTestDispatcher(testScheduler)) { folderExists }

        hooks.uninstall(dir) shouldBe expected

        installer.calls shouldBe if (called) listOf("uninstall $dir") else emptyList()
    }

    @Test
    fun `uninstall uses the project scope without a backup`() =
        runTest {
            val seen = mutableListOf<Pair<HookScope, BackupRef?>>()
            val spy =
                object : HookInstaller by FakeHookInstaller() {
                    override suspend fun uninstall(
                        scope: HookScope,
                        backup: BackupRef?,
                    ): Outcome<Unit, HookError> {
                        seen += scope to backup
                        return Outcome.Ok(Unit)
                    }
                }

            InstallerProjectHooks(spy, UnconfinedTestDispatcher(testScheduler)) { true }.uninstall(dir)

            seen shouldBe listOf(HookScope.Project(dir) to null)
        }

    @Test
    fun `status uses the project scope`() =
        runTest {
            val installer = FakeHookInstaller()

            InstallerProjectHooks(installer, UnconfinedTestDispatcher(testScheduler)) { true }.status(dir)

            installer.calls shouldBe listOf("status $dir")
        }

    @Test
    fun `the folder is checked on the io dispatcher`() =
        runTest {
            val checked = mutableListOf<Path>()
            val io = StandardTestDispatcher(testScheduler)
            val hooks =
                InstallerProjectHooks(FakeHookInstaller(), io) {
                    checked.add(it)
                    true
                }

            val answer = async(UnconfinedTestDispatcher(testScheduler)) { hooks.status(dir) }
            checked.shouldBeEmpty()
            runCurrent()

            checked shouldBe listOf(dir)
            answer.await() shouldBe ProjectHooksState.ABSENT
        }

    @Test
    fun `uninstall checks the folder on the io dispatcher`() =
        runTest {
            val checked = mutableListOf<Path>()
            val io = StandardTestDispatcher(testScheduler)
            val hooks =
                InstallerProjectHooks(FakeHookInstaller(), io) {
                    checked.add(it)
                    true
                }

            val answer = async(UnconfinedTestDispatcher(testScheduler)) { hooks.uninstall(dir) }
            checked.shouldBeEmpty()
            runCurrent()

            checked shouldBe listOf(dir)
            answer.await() shouldBe Outcome.Ok(Unit)
        }

    companion object {
        private val DIR = Path.of("/work/alpha")
        private val file = Path.of("/work/alpha/.claude/local-settings")

        @JvmStatic
        fun statusRows(): Stream<Arguments> =
            Stream.of(
                arguments(false, HookStatus.Installed, ProjectHooksState.ABSENT, false),
                arguments(true, HookStatus.NotInstalled, ProjectHooksState.ABSENT, true),
                arguments(true, HookStatus.Partial(setOf(HookKind.STOP)), ProjectHooksState.PRESENT, true),
                arguments(true, HookStatus.Installed, ProjectHooksState.PRESENT, true),
                arguments(true, HookStatus.Unreadable("bad"), ProjectHooksState.UNKNOWN, true),
            )

        @JvmStatic
        fun uninstallRows(): Stream<Arguments> =
            Stream.of(
                arguments(true, null, Outcome.Ok(Unit), true),
                arguments(
                    true,
                    HookError.SettingsUnreadable(file, "bad"),
                    Outcome.Err(ProjectHooksError.UNREADABLE),
                    true,
                ),
                arguments(
                    true,
                    HookError.WriteFailed(file, "denied"),
                    Outcome.Err(ProjectHooksError.WRITE_FAILED),
                    true,
                ),
                arguments(
                    true,
                    HookError.BackupNotFound(BackupRef("backup0001")),
                    Outcome.Err(ProjectHooksError.WRITE_FAILED),
                    true,
                ),
                arguments(false, null, Outcome.Err(ProjectHooksError.WRITE_FAILED), false),
            )
    }
}
