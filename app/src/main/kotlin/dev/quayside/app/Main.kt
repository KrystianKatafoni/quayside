package dev.quayside.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.impl.agentRuntimeModule
import dev.quayside.agentruntime.impl.hookEventSourceModule
import dev.quayside.agentruntime.impl.hookInstallerModule
import dev.quayside.agentruntime.impl.oneShotPromptModule
import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.appearance.impl.themeFilesModule
import dev.quayside.commandpalette.impl.commandPaletteModule
import dev.quayside.core.designtokens.TerminalColors
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
import dev.quayside.summaries.api.SummariesApi
import dev.quayside.summaries.api.SummarySettingsRepo
import dev.quayside.summaries.impl.summariesModule
import dev.quayside.summaries.impl.summarySettingsModule
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

/** Port of the hook server and of the URLs the installer writes into the settings file (docs/11 §9). */
internal const val HOOK_PORT = 47321

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
    val appScope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    val appearance = runBlocking { appearanceModule(core.storage, core.process, core.homeDir, appScope, core.logger) }
    val looks = appLooks(appearance, appScope)
    // Before the first window (JediTerm reads the installed fonts once, by name): every font the dialog offers.
    registerTerminalFonts(appearance.catalog, core.logger)
    val modules = appModules(core, projects, runBlocking { storedSettings(core) }, appearance, looks)
    val hooks = hookDeps(core, projects)
    val keys = WindowKeys()
    // Before the first application {}: Compose content (the palette) must draw over the Swing terminals (ADR 0012).
    System.setProperty("compose.interop.blending", "true")
    application {
        // Window passes the composition locals of its parent composition to its content, so one AppTheme is enough.
        AppTheme(looks) {
            Window(
                onCloseRequest = {
                    modules.terminal.api.close()
                    exitApplication()
                },
                onKeyEvent = keys::onKeyEvent,
                title = Strings["app.title"],
                state = rememberWindowState(size = DpSize(width = 1280.dp, height = 800.dp)),
            ) {
                MainWindow(core, modules, keys, hooks)
            }
            PopupWindow(modules.settings.notifications.popups)
        }
    }
}

/** The module graph of the window over the already read [projects] and [stored] settings. */
private fun appModules(
    core: CoreDeps,
    projects: ProjectsApi,
    stored: StoredSettings,
    appearance: AppearanceApi,
    looks: StateFlow<AppLook>,
): AppModules {
    val runtime = agentRuntimeModule(core.process, core.clock, core.logger, core.homeDir, injectHookToken = true)
    val inspector = projectInspectorModule(projects, runtime, core.process, core.dispatchers.io, core.logger)
    // Lives as long as the app, not the window (AM-405 risks).
    val sessions =
        sessionsDeps(
            core,
            runtime,
            projects,
            stored.summaries,
            CoroutineScope(SupervisorJob() + core.dispatchers.io),
        )
    // The registry only merges lists on Default; commands run in the window scope on Main (see MainWindow).
    val registry = commandPaletteModule(CoroutineScope(SupervisorJob() + core.dispatchers.default), core.logger)
    val notifications = notificationDeps(core, stored.notifications, sessions, projects)
    val terminal =
        terminalDeps(core, runtime, stored.terminal, notifications, looks)
    return AppModules(
        ProjectDeps(projects, inspector),
        sessions,
        registry,
        terminal,
        SettingsDeps(notifications, stored.summarySettings),
        AppearanceDeps(appearance, looks, themeFilesModule(appearance.catalog, core.dispatchers.io, core.logger)),
    )
}

/** The sessions module fed by the hook server (AM-601, AM-603); both live in [scope], as long as the app. */
private fun sessionsDeps(
    core: CoreDeps,
    runtime: AgentRuntime,
    projects: ProjectsApi,
    summaries: SummariesApi,
    scope: CoroutineScope,
): SessionsApi {
    val hooks = hookEventSourceModule(core.process, core.clock, core.logger, scope, core.homeDir, port = HOOK_PORT)
    return sessionsModule(runtime, hooks, projects, scope, core.clock, core.logger, summaries = summaries)
}

/**
 * The hooks setup: the installer writes URLs with the same [HOOK_PORT] the hook server listens on; its backups go
 * to `hooks-backup` in the app support directory, never into the user's files (AM-607).
 */
private fun hookDeps(
    core: CoreDeps,
    projects: ProjectsApi,
): HookDeps {
    val backupDir = appSupportDir(core.homeDir).resolve("hooks-backup")
    val installer =
        hookInstallerModule(core.homeDir, backupDir, core.clock, core.dispatchers.io, core.logger, port = HOOK_PORT)
    val targets = ProjectHookTargets(projects.projects, core.dispatchers.io)
    return HookDeps(
        installer,
        backupDir,
        HookOfferStore(core.storage),
        targets,
        InstallerProjectHooks(installer, core.dispatchers.io),
    )
}

/** The settings files read in `main` before the window opens, like the project list. */
private class StoredSettings(
    val terminal: TerminalSettingsRepo,
    val notifications: NotificationSettingsRepo,
    val summaries: SummariesApi,
    val summarySettings: SummarySettingsRepo,
)

private suspend fun storedSettings(core: CoreDeps): StoredSettings =
    StoredSettings(
        terminalSettingsModule(core.storage, core.logger),
        notificationSettingsModule(core.storage, core.logger),
        summariesModule(core.storage, oneShotPromptModule(core.process, core.homeDir), core.logger),
        summarySettingsModule(core.storage, core.logger),
    )

/**
 * The terminal module on the login shell over the stored settings, with the colours and font of the applied
 * [looks]. Its bells become notifications of [notifications].
 */
private fun terminalDeps(
    core: CoreDeps,
    runtime: AgentRuntime,
    stored: TerminalSettingsRepo,
    notifications: NotificationDeps,
    looks: StateFlow<AppLook>,
): TerminalDeps {
    val shell = LoginShellProcessRunner.defaultShell()
    val scope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    val settings = appliedTerminalSettings(stored.settings, looks, scope)
    val api = startTerminals(core, shell, settings, appliedTerminalColors(looks, scope))
    forwardBells(api.bells, stored.settings, notifications.center, scope)
    return TerminalDeps(api, stored, shell, runtime::attachCommand, runtime)
}

/**
 * The terminal module on [shell] with [settings] and [colors]. Its scope lives as long as the app (it disposes
 * widgets on Main).
 * Cmd-Q bypasses `onCloseRequest`, so a shutdown hook also kills every shell tree.
 */
private fun startTerminals(
    core: CoreDeps,
    shell: Path,
    settings: StateFlow<TerminalSettings>,
    colors: StateFlow<TerminalColors>,
): TerminalApi {
    val terminal = terminalModule(core.dispatchers, core.logger, shell, settings, colors)
    Runtime.getRuntime().addShutdownHook(Thread { terminal.close() })
    return terminal
}
