package dev.quayside.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.impl.agentRuntimeModule
import dev.quayside.agentruntime.impl.hookEventSourceModule
import dev.quayside.commandpalette.impl.commandPaletteModule
import dev.quayside.core.domain.info
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.impl.notificationSettingsModule
import dev.quayside.notifications.ui.PopupWindow
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.impl.projectInspectorModule
import dev.quayside.projects.impl.projectsModule
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.impl.sessionsModule
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRepo
import dev.quayside.terminal.impl.terminalModule
import dev.quayside.terminal.impl.terminalSettingsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Entry point: builds the dependency graph once, then opens the main window. The project list is read before
 * the window opens: `main` is the one place allowed to block (ENG-C3).
 */
@Suppress("ForbiddenMethodCall")
fun main() {
    // Before the first use of AWT: FileDialog then chooses folders, natively on macOS (AwtDirectoryPicker).
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    val core = productionCoreDeps()
    core.logger.info("app start")
    val projects = runBlocking { projectsModule(core.storage, core.dispatchers.io, core.logger) }
    val modules = appModules(core, projects, runBlocking { storedSettings(core) })
    val keys = WindowKeys()
    // Before the first application {}: Compose content (the palette) must draw over the Swing terminals (ADR 0012).
    System.setProperty("compose.interop.blending", "true")
    application {
        Window(
            onCloseRequest = {
                modules.terminal.api.close()
                exitApplication()
            },
            onKeyEvent = keys::onKeyEvent,
            title = Strings["app.title"],
            state = rememberWindowState(size = DpSize(width = 1280.dp, height = 800.dp)),
        ) {
            MainWindow(core, modules, keys)
        }
        PopupWindow(modules.notifications.popups)
    }
}

/** The module graph of the window over the already read [projects] and [stored] settings. */
private fun appModules(
    core: CoreDeps,
    projects: ProjectsApi,
    stored: StoredSettings,
): AppModules {
    val runtime = agentRuntimeModule(core.process, core.clock, core.logger, core.homeDir, injectHookToken = true)
    val inspector = projectInspectorModule(projects, runtime, core.process, core.dispatchers.io, core.logger)
    // Lives as long as the app, not the window (AM-405 risks).
    val sessions = sessionsDeps(core, runtime, projects, CoroutineScope(SupervisorJob() + core.dispatchers.io))
    // The registry only merges lists on Default; commands run in the window scope on Main (see MainWindow).
    val registry = commandPaletteModule(CoroutineScope(SupervisorJob() + core.dispatchers.default), core.logger)
    val notifications = notificationDeps(core, stored.notifications, sessions, projects)
    val terminal = terminalDeps(core, LoginShellProcessRunner.defaultShell(), runtime, stored.terminal, notifications)
    return AppModules(projects, sessions, inspector, registry, terminal, notifications)
}

/** The sessions module fed by the hook server (AM-601, AM-603); both live in [scope], as long as the app. */
private fun sessionsDeps(
    core: CoreDeps,
    runtime: AgentRuntime,
    projects: ProjectsApi,
    scope: CoroutineScope,
): SessionsApi {
    val hooks = hookEventSourceModule(core.process, core.clock, core.logger, scope, core.homeDir)
    return sessionsModule(runtime, hooks, projects, scope, core.clock, core.logger)
}

/** The settings files read in `main` before the window opens, like the project list. */
private class StoredSettings(
    val terminal: TerminalSettingsRepo,
    val notifications: NotificationSettingsRepo,
)

private suspend fun storedSettings(core: CoreDeps): StoredSettings =
    StoredSettings(
        terminalSettingsModule(core.storage, core.logger),
        notificationSettingsModule(core.storage, core.logger),
    )

/** The terminal module on [shell] over the stored settings. Its bells become notifications of [notifications]. */
private fun terminalDeps(
    core: CoreDeps,
    shell: Path,
    runtime: AgentRuntime,
    stored: TerminalSettingsRepo,
    notifications: NotificationDeps,
): TerminalDeps {
    val api = startTerminals(core, shell, stored.settings)
    val scope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    forwardBells(api.bells, stored.settings, notifications.center, scope)
    return TerminalDeps(api, stored, shell, runtime::attachCommand)
}

/**
 * The terminal module on [shell] with [settings]. Its scope lives as long as the app (it disposes widgets on Main).
 * Cmd-Q bypasses `onCloseRequest`, so a shutdown hook also kills every shell tree.
 */
private fun startTerminals(
    core: CoreDeps,
    shell: Path,
    settings: StateFlow<TerminalSettings>,
): TerminalApi {
    val terminal =
        terminalModule(
            core.dispatchers,
            CoroutineScope(SupervisorJob() + core.dispatchers.default),
            core.logger,
            shell,
            settings,
        )
    Runtime.getRuntime().addShutdownHook(Thread { terminal.close() })
    return terminal
}
