package dev.quayside.app

import dev.quayside.commandpalette.api.FakeCommandRegistry
import dev.quayside.missioncontrol.ui.HistorySettingsController
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.sessions.api.FakeSessions
import kotlinx.coroutines.CoroutineScope

/**
 * The controller of the window "Stare sesje z dysku" over the settings of the fake sessions, with its palette command
 * (`sessions.history`) registered the way `WindowControllers` does it (FR-P9, AM-312).
 */
internal fun historyOf(
    sessions: FakeSessions,
    registry: FakeCommandRegistry,
    projects: ProjectsController,
    missionControl: MissionControlController,
    scope: CoroutineScope,
): HistorySettingsController =
    HistorySettingsController(sessions.historySettings, scope).also {
        registry.register(SessionHistoryCommands(projects, missionControl, it, scope))
    }

/** [this] with the link "Zmień limit" of the list opening [history], as `MainWindow` hands it over. */
internal fun WindowChrome.withHistory(history: HistorySettingsController): WindowChrome =
    WindowChrome(onNewAgent, newAgentEnabled, notifications, supervisor, history::open)
