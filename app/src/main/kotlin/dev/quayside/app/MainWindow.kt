package dev.quayside.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.FrameWindowScope
import dev.quayside.appearance.ui.AppearanceDialog
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.api.CommandRegistry
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.designsystem.AmModalGate
import dev.quayside.core.designsystem.LocalAmModalGate
import dev.quayside.missioncontrol.ui.HistorySettingsDialog
import dev.quayside.missioncontrol.ui.NewAgentDialog
import dev.quayside.notifications.ui.NotificationSettingsDialog
import dev.quayside.summaries.ui.SummarySettingsDialog
import dev.quayside.terminal.ui.TerminalArea
import dev.quayside.terminal.ui.TerminalSettingsDialog
import kotlinx.coroutines.launch
import java.awt.Frame

/** The content and menu of the main window; its controllers live as long as the window (AM-408). */
@Composable
internal fun FrameWindowScope.MainWindow(
    core: CoreDeps,
    modules: AppModules,
    keys: WindowKeys,
    hooks: HookDeps,
) {
    // One modal gate per window: the terminals of this window yield to the dialogs of this window (ADR 0012).
    CompositionLocalProvider(
        LocalAmModalGate provides
            remember {
                AmModalGate()
            },
    ) { WindowContent(core, modules, keys, hooks) }
}

@Composable
private fun FrameWindowScope.WindowContent(
    core: CoreDeps,
    modules: AppModules,
    keys: WindowKeys,
    hooks: HookDeps,
) {
    val scope = rememberCoroutineScope()
    val controllers =
        remember {
            val pickers =
                WindowPickers(
                    AwtDirectoryPicker(window, core.dispatchers.main),
                    AwtThemeFilePicker(window, core.dispatchers.main),
                )
            WindowControllers(core, modules, pickers, scope, hooks)
        }
    WindowBindings(modules.registry, controllers.providers, controllers.palette, keys)
    AppMenu(controllers, modules.projects.api)
    val look by modules.appearance.looks.collectAsState()
    // The UI scale is applied inside the window: Window sets its own density, a scale above it would be lost.
    AppScaled(look.scale) {
        QuaysideApp(
            controllers = AppControllers(controllers.projects, controllers.missionControl, controllers.palette),
            terminal = remember(controllers) { terminalSlotOf(controllers) },
            chrome = chromeOf(controllers, modules),
            keyCaps = remember { windowKeyCaps() },
        )
        WindowDialogs(controllers, hooks)
    }
    // Outside the scale: the preview of the dialog scales the base density itself (previewDensity).
    AppearanceDialog(controllers.appearance) { AppearancePreviewSlot() }
    PopupAttachHandler(modules, controllers.attacher)
    LaunchedEffect(Unit) { controllers.hookSetup.offerAtStart() }
}

/**
 * What the top bar and the status bar need (AM-816): "Nowy agent" enabled as its menu item, the voice chip over the
 * notification settings, and the version of Claude Code, read once when the window opens. The health check behind
 * it starts three processes (`claude --version`, `daemon status`, `agents --json`).
 */
@Composable
private fun chromeOf(
    controllers: WindowControllers,
    modules: AppModules,
): WindowChrome {
    val enabled = menuStateOf(controllers, modules.projects.api).newAgent
    val supervisor by produceState<String?>(initialValue = null, modules.terminal.runtime) {
        value =
            readSupervisor(modules.terminal.runtime)
    }
    return WindowChrome(
        onNewAgent = { openNewAgent(controllers.projects, controllers.newAgent) },
        newAgentEnabled = enabled,
        notifications =
            NotificationChrome(
                modules.settings.notifications.settings.settings,
                controllers.notificationSettings::open,
            ),
        supervisor = supervisor,
        onHistorySettings = controllers.historySettings::open,
    )
}

/** The dialogs drawn over the window content: "Nowy agent", the settings and the hooks. */
@Composable
private fun WindowDialogs(
    controllers: WindowControllers,
    hooks: HookDeps,
) {
    NewAgentDialog(controllers.newAgent)
    TerminalSettingsDialog(controllers.terminalSettings)
    NotificationSettingsDialog(controllers.notificationSettings, controllers.soundPreview)
    SummarySettingsDialog(controllers.summarySettings)
    HistorySettingsDialog(controllers.historySettings)
    HookSetupDialog(controllers.hookSetup, hooks.backupDir)
}

/** The terminal of the window; remembered, so the movable panel keeps its state across recompositions. */
private fun terminalSlotOf(controllers: WindowControllers): TerminalSlot =
    TerminalSlot(
        panel = { modifier, onFullScreen ->
            TerminalArea(controllers.terminal, controllers.terminalKeys::onKey, modifier, onFullScreen = onFullScreen)
        },
        attacher = controllers.attacher,
        mode = controllers.mode,
        terminalKey = controllers.terminal.activeKey,
        pane = controllers.pane,
    )

/**
 * Makes the Attach action of a popup open the session's terminal tab and bring this window to the front while the
 * window is shown. Runs on the window scope (Main), because the popup action comes from a background scope.
 */
@Composable
private fun FrameWindowScope.PopupAttachHandler(
    modules: AppModules,
    attacher: SessionAttacher,
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        modules.settings.notifications.attach.handler = { id ->
            scope.launch {
                val sessions = modules.sessions.monitor.snapshot.value.sessions
                attacher.attachSession(id, sessions, modules.projects.api.projects.value)
                window.extendedState = Frame.NORMAL
                window.toFront()
            }
        }
        onDispose { modules.settings.notifications.attach.handler = null }
    }
}

/** Registers the command [providers] and routes the window keys to [palette] while the window is shown. */
@Composable
private fun WindowBindings(
    registry: CommandRegistry,
    providers: List<CommandProvider>,
    palette: CommandPaletteController,
    keys: WindowKeys,
) {
    DisposableEffect(Unit) {
        val registrations = providers.map(registry::register)
        onDispose { registrations.forEach { it.close() } }
    }
    DisposableEffect(Unit) {
        val handle = keys.attach(palette::onKeyEvent)
        onDispose { handle.close() }
    }
}
