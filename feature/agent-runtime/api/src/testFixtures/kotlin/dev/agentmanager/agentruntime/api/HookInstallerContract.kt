package dev.agentmanager.agentruntime.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path

/**
 * Behaviour every [HookInstaller] must have (ENG-T2), checked for both [HookScope.Global] and
 * [HookScope.Project]. Runs on [FakeHookInstaller] and, from AM-602, on the file-based installer
 * over a temporary home and project directory.
 */
public abstract class HookInstallerContract {
    /** Scope a parameterized test runs in; turned into a [HookScope] after [create]. */
    public enum class ScopeKind { GLOBAL, PROJECT }

    /**
     * Returns an installer over fresh settings that contain none of our hooks, in any scope. A
     * subclass that needs real directories may change [projectDir] here.
     */
    public abstract fun create(): HookInstaller

    /** Project directory behind [HookScope.Project] for the installer returned by the latest [create]. */
    protected open val projectDir: Path get() = A_PROJECT_DIR

    private fun ScopeKind.scope(): HookScope =
        when (this) {
            ScopeKind.GLOBAL -> HookScope.Global
            ScopeKind.PROJECT -> HookScope.Project(projectDir)
        }

    private fun ScopeKind.other(): ScopeKind = if (this == ScopeKind.GLOBAL) ScopeKind.PROJECT else ScopeKind.GLOBAL

    private suspend fun HookInstaller.installed(scope: HookScope): BackupRef =
        install(scope).getOrNull().shouldNotBeNull()

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `status before install is NotInstalled`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()

            installer.status(kind.scope()) shouldBe HookStatus.NotInstalled
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `install returns a backup and makes the status Installed`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()
            val scope = kind.scope()

            installer.install(scope).shouldBeInstanceOf<Outcome.Ok<BackupRef>>()

            installer.status(scope) shouldBe HookStatus.Installed
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `second install changes nothing but still returns a backup`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()
            val scope = kind.scope()
            installer.installed(scope)

            installer.install(scope).shouldBeInstanceOf<Outcome.Ok<BackupRef>>()

            installer.status(scope) shouldBe HookStatus.Installed
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `uninstall without a backup makes the status NotInstalled`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()
            val scope = kind.scope()
            installer.installed(scope)

            installer.uninstall(scope, backup = null) shouldBe Outcome.Ok(Unit)

            installer.status(scope) shouldBe HookStatus.NotInstalled
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `uninstall with the backup of the first install restores settings without hooks`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()
            val scope = kind.scope()
            val backup = installer.installed(scope)

            installer.uninstall(scope, backup) shouldBe Outcome.Ok(Unit)

            installer.status(scope) shouldBe HookStatus.NotInstalled
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `uninstall with an unknown backup returns BackupNotFound and keeps the hooks`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()
            val scope = kind.scope()
            installer.installed(scope)
            val unknown = BackupRef("no-such-backup")

            installer.uninstall(scope, unknown) shouldBe Outcome.Err(HookError.BackupNotFound(unknown))

            installer.status(scope) shouldBe HookStatus.Installed
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScopeKind::class)
    public fun `install in one scope does not change the other scope`(kind: ScopeKind): Unit =
        runTest {
            val installer = create()

            installer.installed(kind.scope())

            installer.status(kind.other().scope()) shouldBe HookStatus.NotInstalled
        }
}
