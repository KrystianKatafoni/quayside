package dev.quayside.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.quayside.accounts.api.AccountPaths
import dev.quayside.accounts.api.AccountsApi
import dev.quayside.accounts.impl.accountsModule
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.impl.agentRuntimeModule
import dev.quayside.agentruntime.impl.hookEventSourceModule
import dev.quayside.agentruntime.impl.hookInstallerModule
import dev.quayside.agentruntime.impl.oneShotPromptModule
import dev.quayside.agentruntime.impl.sessionHistoryModule
import dev.quayside.agentruntime.impl.workspaceTrustModule
import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.appearance.impl.themeFilesModule
import dev.quayside.commandpalette.impl.commandPaletteModule
import dev.quayside.core.designsystem.amLogoImage
import dev.quayside.core.designsystem.rememberAmLogoPainter
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.info
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.impl.notificationSettingsModule
import dev.quayside.notifications.ui.PopupWindow
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.impl.projectInspectorModule
import dev.quayside.projects.impl.projectsModule
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.impl.sessionHistorySettingsModule
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

private const val DOCK_ICON_PX = 256
private val WINDOW_ICON_SIZE = 32.dp

/**
 * Entry point: builds the dependency graph once, then opens the main window. The project list is read before
 * the window opens: `main` is the one place allowed to block (ENG-C3).
 */
@Suppress("ForbiddenMethodCall")
fun main() {
    val core = startedCore()
    val projects = runBlocking { projectsModule(core.storage, core.dispatchers.io, core.logger) }
    val appScope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    val appearance = runBlocking { appearanceModule(core.storage, core.process, core.homeDir, appScope, core.logger) }
    val looks = appLooks(appearance, appScope)
    prepareWindowing(core.logger, appearance)
    val (stored, accounts) = runBlocking { storedSettings(core) to accountsDeps(core) }
    val look = AppearanceDeps(appearance, looks, themeFilesModule(appearance, core.dispatchers.io, core.logger))
    val modules = appModules(core, projects, stored, accounts, look)
    val hooks = hookDeps(core, projects)
    val keys = WindowKeys()
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
                icon = rememberAmLogoPainter(WINDOW_ICON_SIZE),
                state = rememberWindowState(size = DpSize(width = 1280.dp, height = 800.dp)),
            ) {
                MainWindow(core, modules, keys, hooks)
            }
            PopupWindow(modules.settings.notifications.popups)
        }
    }
}

/** The production dependencies, created after the folder dialog setting and before anything else is opened. */
private fun startedCore(): CoreDeps {
    enableNativeFolderDialog()
    return productionCoreDeps().also { it.logger.info("app start") }
}

/** Before the first use of AWT: FileDialog then chooses folders, natively on macOS (AwtDirectoryPicker). */
private fun enableNativeFolderDialog() {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
}

/** Before the first window: terminal fonts, interop blending and the Dock icon. */
private fun prepareWindowing(
    logger: Logger,
    appearance: AppearanceApi,
) {
    // JediTerm reads the installed fonts once, by name: register every font the dialog offers.
    registerTerminalFonts(appearance.catalog.value, logger)
    // Compose content (the palette) must draw over the Swing terminals (ADR 0012).
    System.setProperty("compose.interop.blending", "true")
    // The Dock shows the mark only through Taskbar (macOS ignores Window(icon)); the full drawing, 256 px.
    installDockIcon(amLogoImage(DOCK_ICON_PX), logger)
}

/** The module graph of the window over what was read before it opens: projects, stored settings, accounts, looks. */
private fun appModules(
    core: CoreDeps,
    projects: ProjectsApi,
    stored: StoredSettings,
    accounts: AccountsApi,
    appearance: AppearanceDeps,
): AppModules {
    val runtime = agentRuntimeModule(core.process, core.clock, core.logger, core.homeDir, injectHookToken = true)
    val inspector = projectInspectorModule(projects, runtime, core.process, core.dispatchers.io, core.logger)
    // Lives as long as the app, not the window (AM-405 risks).
    val sessions =
        sessionsDeps(core, runtime, projects, stored, CoroutineScope(SupervisorJob() + core.dispatchers.io))
    // The registry only merges lists on Default; commands run in the window scope on Main (see MainWindow).
    val registry = commandPaletteModule(CoroutineScope(SupervisorJob() + core.dispatchers.default), core.logger)
    val notifications = notificationDeps(core, stored.notifications, sessions, projects)
    val terminal =
        terminalDeps(core, runtime, stored.terminal, notifications, appearance.looks)
    return AppModules(
        ProjectDeps(projects, inspector, workspaceTrustModule(core.homeDir, core.dispatchers.io, core.logger)),
        sessions,
        registry,
        terminal,
        SettingsDeps(notifications, stored.summarySettings, stored.layout, stored.history, accounts),
        appearance,
    )
}

/** The sessions module fed by the hook server (AM-601, AM-603); both live in [scope], as long as the app. */
private fun sessionsDeps(
    core: CoreDeps,
    runtime: AgentRuntime,
    projects: ProjectsApi,
    stored: StoredSettings,
    scope: CoroutineScope,
): SessionsApi {
    val claudeHome = core.homeDir.resolve(".claude")
    val hooks = hookEventSourceModule(core.process, core.clock, core.logger, scope, core.homeDir, port = HOOK_PORT)
    return sessionsModule(
        runtime,
        hooks,
        projects,
        scope,
        core.clock,
        core.logger,
        summaries = stored.summaries,
        claudeHome = claudeHome,
        io = core.dispatchers.io,
        history = sessionHistoryModule(claudeHome, core.dispatchers.io, core.logger),
        historySettings = stored.history,
    )
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
    val layout: WindowLayoutDeps,
    val history: SessionHistorySettingsRepo,
)

private suspend fun storedSettings(core: CoreDeps): StoredSettings {
    val layout = WindowLayoutStore(core.storage, core.logger)
    return StoredSettings(
        terminalSettingsModule(core.storage, core.logger),
        notificationSettingsModule(core.storage, core.logger),
        summariesModule(core.storage, oneShotPromptModule(core.process, core.homeDir), core.logger),
        summarySettingsModule(core.storage, core.logger),
        WindowLayoutDeps(layout, layout.load()),
        sessionHistorySettingsModule(core.storage, core.logger),
    )
}

/** The accounts module over `accounts.json` and the backup directory `accounts-backup` (AM-1002). */
private suspend fun accountsDeps(core: CoreDeps): AccountsApi =
    accountsModule(
        core.storage,
        AccountPaths(core.homeDir, appSupportDir(core.homeDir).resolve("accounts-backup")),
        core.clock,
        core.dispatchers.io,
        core.logger,
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
