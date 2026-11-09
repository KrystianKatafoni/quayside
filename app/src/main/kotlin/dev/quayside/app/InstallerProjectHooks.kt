package dev.quayside.app

import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.Outcome
import dev.quayside.projects.ui.ProjectHooks
import dev.quayside.projects.ui.ProjectHooksError
import dev.quayside.projects.ui.ProjectHooksState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** [ProjectHooks] over [installer] with `HookScope.Project`; checks the folder on [io] first (AM-609 rule). */
internal class InstallerProjectHooks(
    private val installer: HookInstaller,
    private val io: CoroutineDispatcher,
    private val isDirectory: (Path) -> Boolean = Files::isDirectory,
) : ProjectHooks {
    override suspend fun status(dir: Path): ProjectHooksState {
        if (!exists(dir)) return ProjectHooksState.ABSENT
        return when (installer.status(HookScope.Project(dir))) {
            HookStatus.NotInstalled -> ProjectHooksState.ABSENT
            HookStatus.Installed, is HookStatus.Partial -> ProjectHooksState.PRESENT
            is HookStatus.Unreadable -> ProjectHooksState.UNKNOWN
        }
    }

    override suspend fun uninstall(dir: Path): Outcome<Unit, ProjectHooksError> {
        if (!exists(dir)) return Outcome.Err(ProjectHooksError.WRITE_FAILED)
        return when (val result = installer.uninstall(HookScope.Project(dir), backup = null)) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> Outcome.Err(map(result.error))
        }
    }

    private suspend fun exists(dir: Path): Boolean = withContext(io) { isDirectory(dir) }

    private fun map(error: HookError): ProjectHooksError =
        when (error) {
            is HookError.SettingsUnreadable -> ProjectHooksError.UNREADABLE
            is HookError.WriteFailed, is HookError.BackupNotFound -> ProjectHooksError.WRITE_FAILED
        }
}
