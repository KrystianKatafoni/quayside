package dev.quayside.app

import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRules
import dev.quayside.terminal.ui.ShellStart
import dev.quayside.terminal.ui.TerminalProject
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * [ShellStart] over the terminal settings (AM-508, FR-T7): label from the shell profile, directory from the start
 * directory choice. Reads [settings] and [selectedSessionDir] at the moment of the call, never from a combined flow
 * (a `stateIn` may lag one step behind). [selectedSessionDir] gives the `cwd` of the session selected in Mission
 * Control when it belongs to the project, else null.
 */
internal class SettingsShellStart(
    private val settings: StateFlow<TerminalSettings>,
    private val loginShell: Path,
    private val home: Path,
    private val selectedSessionDir: (ProjectId) -> Path?,
) : ShellStart {
    override val label: String get() = TerminalSettingsRules.shellLabelOf(settings.value.shell, loginShell)

    override fun dirOf(project: TerminalProject): Path =
        TerminalSettingsRules.startDirOf(
            settings.value.startDirectory,
            project.dir,
            selectedSessionDir(project.id),
            home,
        )
}

/**
 * The `cwd` of the session selected in [missionControl] when it belongs to the asked project, else null. Reads
 * [MissionControlController.attachTarget] and [snapshot] afresh at every call (AM-508).
 */
internal fun selectedSessionDirOf(
    missionControl: MissionControlController,
    snapshot: StateFlow<SessionsSnapshot>,
): (ProjectId) -> Path? =
    { project ->
        val target = missionControl.attachTarget()?.takeIf { it.project.id == project }
        target?.let { chosen ->
            snapshot.value.sessions
                .firstOrNull { it.id == chosen.session }
                ?.cwd
        }
    }
