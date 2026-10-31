package dev.agentmanager.app

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
import dev.agentmanager.commandpalette.api.FakeCommandRegistry
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.missioncontrol.ui.MissionControlController
import dev.agentmanager.projects.api.FakeProjectInspector
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.ui.DirectoryPicker
import dev.agentmanager.projects.ui.ProjectsController
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.terminal.ui.TerminalArea
import dev.agentmanager.terminal.ui.TerminalController
import kotlinx.coroutines.CoroutineScope
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
): CommandPaletteController {
    val built = mutableListOf<CommandPaletteController>()
    val keys = WindowKeys()
    setContent {
        val scope = rememberCoroutineScope()
        val controller = remember { projectsController(projects, sessions, scope) }
        val missionControl = remember { missionControlController(projects, sessions, controller, scope) }
        val registry = remember { FakeCommandRegistry(scope) }
        val palette = remember { CommandPaletteController(registry, scope, NoOpLogger).also { built += it } }
        remember { registry.register(AppCommands(projects, controller, missionControl, palette, scope)) }
        val terminalController =
            rememberTerminal(terminal, scope) {
                TerminalWiring(projects, sessions, controller, missionControl, scope)
                    .register(registry, it, attachCommand)
            }
        PaletteKeys(keys, palette)
        KeysBox(keys) {
            AgentManagerApp(
                projects = controller,
                missionControl = missionControl,
                palette = palette,
                terminalPanel = terminalPanel(terminalController, terminal),
            )
        }
    }
    waitUntil { built.isNotEmpty() }
    return built.single()
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
    fun register(
        registry: FakeCommandRegistry,
        terminal: TerminalController,
        attachCommand: (SessionId) -> ShellCommand,
    ) {
        terminal.followSessions(sessions.snapshot, scope)
        registry.register(TerminalCommands(projects, controller, missionControl, terminal, scope))
        val attacher = SessionAttacher(missionControl, terminal, attachCommand, NoOpLogger)
        registry.register(AttachCommands(controller, missionControl, attacher, scope))
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
    register: (TerminalController) -> Unit,
): TerminalController? =
    remember {
        terminals?.let { TerminalController(it, scope, NoOpLogger, "zsh") }?.also(register)
    }

/** The terminal panel slot of the window: the area over [controller] with markers for the widgets of [terminals]. */
private fun terminalPanel(
    controller: TerminalController?,
    terminals: RecordingTerminals?,
): (@Composable (Modifier) -> Unit)? {
    if (controller == null) return null
    return { modifier -> TerminalArea(controller, { false }, modifier, terminals?.paneContent) }
}
