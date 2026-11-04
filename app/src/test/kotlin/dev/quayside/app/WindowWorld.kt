package dev.quayside.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.commandpalette.api.FakeCommandRegistry
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.missioncontrol.ui.NewAgentDialog
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.ui.NotificationSettingsController
import dev.quayside.notifications.ui.NotificationSettingsDialog
import dev.quayside.projects.api.FakeProjectInspector
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.ui.DirectoryPicker
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.ui.ShellStart
import dev.quayside.terminal.ui.TerminalArea
import dev.quayside.terminal.ui.TerminalController
import dev.quayside.terminal.ui.TerminalSettingsController
import dev.quayside.terminal.ui.TerminalSettingsDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * The window of the app on fakes: sidebar, Mission Control and the palette over [AppCommands], registered in a
 * [FakeCommandRegistry] the way `Main.kt` registers it in the real one. `MenuBar` is not part of it
 * (it does not work in `runComposeUiTest`), so tests open the palette with [palette].
 *
 * With [terminal] the window also gets the terminal panel and its palette commands; the widgets of the fake sessions
 * are replaced by markers tagged `terminal.content.<session id>` (see [RecordingTerminals]).
 *
 * With [settings] the window also gets the settings dialog and its palette command (AM-508); shell tabs get
 * their label and directory from [SettingsShellStart]; without, from [PlainShellStart].
 *
 * With a notification repo in [settings] it also gets the notification settings dialog and its palette command
 * (AM-704); its list of voices comes from [SettingsRepos.voices].
 *
 * With [terminal] the window also registers [AttachCommands] (AM-505); [attachCommand] stands for
 * `AgentRuntime.attachCommand`.
 *
 * Window keys: [WindowKeys] sits on a focused `Box` ([WindowTags.KEYS]) that wraps the content, the way
 * `Window(onKeyEvent = keys::onKeyEvent)` does in `Main.kt`; `runComposeUiTest` has no `Window`. Press keys
 * with `onNodeWithTag(WindowTags.KEYS).performKeyInput { … }`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showWindow(
    projects: FakeProjectsApi,
    sessions: FakeSessions = FakeSessions(),
    terminal: RecordingTerminals? = null,
    attachCommand: (SessionId) -> ShellCommand = ::defaultAttachCommand,
    settings: SettingsRepos = SettingsRepos(),
): CommandPaletteController = showWindowOf(WindowSetup(projects, sessions, terminal, attachCommand, settings))

/**
 * What [showWindowOf] builds the window from. [starts] stands for the `SessionCommands` of the "Nowy agent" dialog
 * (default: [sessions] itself); a [RecordingStarts] shows which spec reached `start`. The window fills [keys] with
 * the bridge of ⌘ keys (the way `MainWindow` does for a focused terminal, AM-503): `MenuBar`, which carries ⌘N in
 * the real window, does not work in `runComposeUiTest`.
 */
internal class WindowSetup(
    val projects: FakeProjectsApi,
    val sessions: FakeSessions = FakeSessions(),
    val terminal: RecordingTerminals? = null,
    val attachCommand: (SessionId) -> ShellCommand = ::defaultAttachCommand,
    val settings: SettingsRepos = SettingsRepos(),
    val starts: SessionCommands? = null,
) {
    var keys: TerminalKeys? = null
}

/** [showWindow] for a [WindowSetup] with the "Nowy agent" dialog, its command and its button (AM-608). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showWindowOf(setup: WindowSetup): CommandPaletteController {
    val built = mutableListOf<CommandPaletteController>()
    val keys = WindowKeys()
    val look = appearanceModule().catalog.defaultLook()
    setContent {
        AppTheme(look) { WindowContent(setup, keys) { built += it } }
    }
    waitUntil { built.isNotEmpty() }
    return built.single()
}

/** The window content of [setup] under [AppTheme]; [onPalette] gets the palette for the test. */
@Composable
private fun WindowContent(
    setup: WindowSetup,
    keys: WindowKeys,
    onPalette: (CommandPaletteController) -> Unit,
) {
    val scope = rememberCoroutineScope()
    with(remember { windowCore(setup, scope, onPalette) }) {
        val terminal = rememberTerminalPart(setup, scope)
        val agent =
            remember { AgentWiring(setup, projects, missionControl, scope).also { it.register(registry, palette) } }
        PaletteKeys(keys, palette)
        KeysBox(keys) {
            QuaysideApp(
                projects = projects,
                missionControl = missionControl,
                palette = palette,
                terminalPanel = terminalPanel(terminal.controller, setup.terminal),
                attacher = terminal.wiring.attacher,
                onNewAgent = agent::openForSelection,
            )
            SettingsHosts(setup.settings, registry, WindowParts(projects, missionControl, scope))
            NewAgentDialog(agent.newAgent)
        }
    }
}

/** The controllers every window has, and the registry the palette reads: the sidebar, Mission Control, the palette. */
private data class WindowCore(
    val projects: ProjectsController,
    val missionControl: MissionControlController,
    val registry: FakeCommandRegistry,
    val palette: CommandPaletteController,
)

/** The terminal controller of the window (null without one) and the wiring of its commands. */
private class TerminalPart(
    val wiring: TerminalWiring,
    val controller: TerminalController?,
)

@Composable
private fun WindowCore.rememberTerminalPart(
    setup: WindowSetup,
    scope: CoroutineScope,
): TerminalPart {
    val shellStart = remember { shellStartOf(setup.settings.terminal, missionControl, setup.sessions) }
    val wiring = remember { TerminalWiring(setup.projects, setup.sessions, projects, missionControl, scope) }
    val controller =
        rememberTerminal(setup.terminal, scope, shellStart) { wiring.register(registry, it, setup.attachCommand) }
    return TerminalPart(wiring, controller)
}

/** Builds the [WindowCore] of [setup] and registers [AppCommands]; [onPalette] gets the palette for the test. */
private fun windowCore(
    setup: WindowSetup,
    scope: CoroutineScope,
    onPalette: (CommandPaletteController) -> Unit,
): WindowCore {
    val controller = projectsController(setup.projects, setup.sessions, scope)
    val missionControl = missionControlController(setup.projects, setup.sessions, controller, scope)
    val registry = FakeCommandRegistry(scope)
    val palette = CommandPaletteController(registry, scope, NoOpLogger).also(onPalette)
    registry.register(AppCommands(setup.projects, controller, missionControl, palette, scope))
    return WindowCore(controller, missionControl, registry, palette)
}

/**
 * The "Nowy agent" controller of the window, its palette command and the ⌘ key bridge (AM-608); its `onStarted` is
 * the one `MainWindow` uses.
 */
private class AgentWiring(
    private val setup: WindowSetup,
    private val projects: ProjectsController,
    private val missionControl: MissionControlController,
    private val scope: CoroutineScope,
) {
    val newAgent =
        NewAgentController(
            setup.starts ?: setup.sessions,
            setup.projects,
            scope,
            NoOpLogger,
            selectStartedSession(projects, missionControl),
        )

    fun register(
        registry: FakeCommandRegistry,
        palette: CommandPaletteController,
    ) {
        val others = MutableStateFlow(value = false)
        registry.register(NewAgentCommands(projects, missionControl, setup.projects, newAgent, scope, others))
        setup.keys = TerminalKeys(registry, palette, scope)
    }

    /** The button of the project page: opens the dialog on the project selected in the sidebar. */
    fun openForSelection() = newAgent.open(projects.selection.value)
}

/**
 * The settings repos whose dialogs the window shows; a dialog without its repo is not shown. With [hooks] the window
 * also gets the "Hooki Claude Code" dialog and its palette command (AM-607); [hookOffer] is then required (a store on
 * a temp directory of the test). The start offer runs only with [hookOfferAtStart], the way `MainWindow` does.
 */
internal class SettingsRepos(
    val terminal: FakeTerminalSettingsRepo? = null,
    val notifications: FakeNotificationSettingsRepo? = null,
    val voices: VoiceCatalog = FakeVoiceCatalog(emptyList()),
    val hooks: FakeHookInstaller? = null,
    val hookOffer: HookOfferStore? = null,
    val hookOfferAtStart: Boolean = false,
)

/** The backup folder the hooks dialog of the window shows. */
internal val WINDOW_HOOK_BACKUP_DIR: Path = Path.of(System.getProperty("java.io.tmpdir")).resolve("hooks-backup")

/** What the settings dialog and its command need from the window. */
private class WindowParts(
    val projects: ProjectsController,
    val missionControl: MissionControlController,
    val scope: CoroutineScope,
)

/** The settings dialogs of [repos] with their palette commands. */
@Composable
private fun SettingsHosts(
    repos: SettingsRepos,
    registry: FakeCommandRegistry,
    window: WindowParts,
) {
    val notifications =
        remember { repos.notifications?.let { NotificationSettingsController(it, repos.voices, window.scope) } }
    SettingsDialogHost(repos.terminal, registry, window, notifications?.dialogOpen(window.scope))
    NotificationDialogHost(notifications, registry, window)
    HookDialogHost(repos, registry, window)
}

/** The hooks dialog over [SettingsRepos.hooks] with its palette command registered; nothing without one. */
@Composable
private fun HookDialogHost(
    repos: SettingsRepos,
    registry: FakeCommandRegistry,
    window: WindowParts,
) {
    val installer = repos.hooks ?: return
    val offer = checkNotNull(repos.hookOffer) { "SettingsRepos.hooks needs hookOffer on a temp directory" }
    val controller =
        remember {
            HookSetupController(installer, offer, window.scope, NoOpLogger).also {
                val others = MutableStateFlow(value = false)
                registry.register(HookSetupCommands(window.projects, window.missionControl, it, window.scope, others))
            }
        }
    HookSetupDialog(controller, WINDOW_HOOK_BACKUP_DIR)
    if (repos.hookOfferAtStart) LaunchedEffect(Unit) { controller.offerAtStart() }
}

/** The settings dialog over [repo] with its palette command registered; nothing without a repo. */
@Composable
private fun SettingsDialogHost(
    repo: FakeTerminalSettingsRepo?,
    registry: FakeCommandRegistry,
    window: WindowParts,
    otherDialogOpen: StateFlow<Boolean>?,
) {
    val controller =
        remember {
            repo?.let { TerminalSettingsController(it, window.scope) }?.also {
                val other = otherDialogOpen ?: MutableStateFlow(value = false)
                registry.register(
                    TerminalSettingsCommands(window.projects, window.missionControl, it, window.scope, other),
                )
            }
        }
    controller?.let { TerminalSettingsDialog(it) }
}

/** The notification settings dialog of [controller] with its palette command registered; nothing without one. */
@Composable
private fun NotificationDialogHost(
    controller: NotificationSettingsController?,
    registry: FakeCommandRegistry,
    window: WindowParts,
) {
    remember {
        controller?.let {
            registry.register(NotificationSettingsCommands(window.projects, window.missionControl, it, window.scope))
        }
    }
    controller?.let { NotificationSettingsDialog(it) }
}

/** Routes the palette's keys through [keys] while the window is shown. */
@Composable
private fun PaletteKeys(
    keys: WindowKeys,
    palette: CommandPaletteController,
) {
    DisposableEffect(Unit) {
        val handle = keys.attach(palette::onKeyEvent)
        onDispose { handle.close() }
    }
}

/** Registers the Terminal and Attach commands and feeds the tab dots (the way `Main.kt` does, AM-510). */
private class TerminalWiring(
    private val projects: FakeProjectsApi,
    private val sessions: FakeSessions,
    private val controller: ProjectsController,
    private val missionControl: MissionControlController,
    private val scope: CoroutineScope,
) {
    /** The attacher of the registered terminal, which the mouse paths of `QuaysideApp` use; null without one. */
    var attacher: SessionAttacher? = null
        private set

    fun register(
        registry: FakeCommandRegistry,
        terminal: TerminalController,
        attachCommand: (SessionId) -> ShellCommand,
    ) {
        terminal.followSessions(sessions.snapshot, scope)
        registry.register(TerminalCommands(projects, controller, missionControl, terminal, scope))
        val created = SessionAttacher(missionControl, terminal, attachCommand, NoOpLogger)
        attacher = created
        registry.register(AttachCommands(controller, missionControl, created, scope))
    }
}

/** `claude attach <id>` in the temp dir, which exists, so [RecordingTerminals] can "start" it. */
private fun defaultAttachCommand(id: SessionId): ShellCommand =
    ShellCommand(listOf("claude", "attach", id.value), Path.of(System.getProperty("java.io.tmpdir")))

private fun missionControlController(
    projects: FakeProjectsApi,
    sessions: FakeSessions,
    controller: ProjectsController,
    scope: CoroutineScope,
): MissionControlController =
    MissionControlController(
        SessionsApi(monitor = sessions, commands = sessions),
        projects,
        controller.selection,
        TestClock(),
        scope,
        NoOpLogger,
        1.hours,
    )

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
    waitUntilExactlyOneExists(hasTestTag(tag))
    return onNodeWithTag(tag)
}

/** The focused `Box` that stands for `Window(onKeyEvent = keys::onKeyEvent)`; tagged [WindowTags.KEYS]. */
@Composable
private fun KeysBox(
    keys: WindowKeys,
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(WindowTags.KEYS)
            .focusRequester(focus)
            .onKeyEvent(keys::onKeyEvent)
            .focusable(),
    ) { content() }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private fun projectsController(
    projects: FakeProjectsApi,
    sessions: FakeSessions,
    scope: CoroutineScope,
): ProjectsController =
    ProjectsController(
        projects = projects,
        inspector = FakeProjectInspector(projects = projects),
        sessions = SessionsApi(monitor = sessions, commands = sessions),
        picker = DirectoryPicker { null },
        scope = scope,
        logger = NoOpLogger,
    )

/** The terminal controller over [terminals], handed to [register] once; null without [terminals]. */
@Composable
private fun rememberTerminal(
    terminals: RecordingTerminals?,
    scope: CoroutineScope,
    shellStart: ShellStart,
    register: (TerminalController) -> Unit,
): TerminalController? =
    remember {
        terminals?.let { TerminalController(it, scope, NoOpLogger, shellStart) }?.also(register)
    }

/** Home of the shells started from the terminal repo with `StartDirectory.HOME`; a directory that exists. */
internal val WINDOW_HOME: Path = Path.of(System.getProperty("java.io.tmpdir"))

/**
 * The shell start of the window: [SettingsShellStart] over [settings] and the session selected in [missionControl]
 * (login shell `zsh`, home [WINDOW_HOME]), or [PlainShellStart] without [settings].
 */
private fun shellStartOf(
    settings: FakeTerminalSettingsRepo?,
    missionControl: MissionControlController,
    sessions: FakeSessions,
): ShellStart =
    settings?.let {
        SettingsShellStart(
            it.settings,
            Path.of("/bin/zsh"),
            WINDOW_HOME,
            selectedSessionDirOf(missionControl, sessions.snapshot),
        )
    } ?: PlainShellStart()

/** The terminal panel slot of the window: the area over [controller] with markers for the widgets of [terminals]. */
private fun terminalPanel(
    controller: TerminalController?,
    terminals: RecordingTerminals?,
): (@Composable (Modifier) -> Unit)? {
    if (controller == null) return null
    return { modifier -> TerminalArea(controller, { false }, modifier, terminals?.paneContent) }
}
