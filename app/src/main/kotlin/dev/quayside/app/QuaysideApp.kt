package dev.quayside.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quayside.commandpalette.ui.CommandPalette
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.missioncontrol.ui.GlobalViews
import dev.quayside.missioncontrol.ui.MissionControl
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlHandlers
import dev.quayside.missioncontrol.ui.SessionKeyCaps
import dev.quayside.missioncontrol.ui.ShortcutsHelp
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsSidebar
import dev.quayside.terminal.ui.TabKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/** The three controllers every window has. */
internal class AppControllers(
    val projects: ProjectsController,
    val missionControl: MissionControlController,
    val palette: CommandPaletteController,
)

/**
 * The terminal parts of a window: [panel] draws the terminal and gets the size and the full screen action; [attacher]
 * serves the mouse paths; [mode] says which layout is shown and [terminalKey] is the key of the active tab, which the
 * session bar of the terminal mode shows. All optional: a window can run without a terminal.
 */
internal data class TerminalSlot(
    val panel: (@Composable (Modifier, (() -> Unit)?) -> Unit)? = null,
    val attacher: SessionAttacher? = null,
    val mode: WindowModeState? = null,
    val terminalKey: StateFlow<TabKey?>? = null,
)

/**
 * The window content: the top bar, then the projects sidebar with the global views and the cheat sheet under it, next
 * to Mission Control (AM-304, AM-405, AM-407), or the terminal mode, and the status bar at the bottom of both. A shown
 * global view clears the project in the sidebar (AM-407, decision 1).
 *
 * [terminal] says how the terminal is drawn and used (see [TerminalSlot]); the terminal panel draws nothing while
 * there are no tabs and no error (AM-503). [chrome] is what the top bar and the status bar need (AM-816);
 * [keyCaps] are the shortcut marks next to the actions (AM-809).
 */
@Composable
internal fun QuaysideApp(
    controllers: AppControllers,
    modifier: Modifier = Modifier,
    terminal: TerminalSlot = TerminalSlot(),
    chrome: WindowChrome = WindowChrome(),
    keyCaps: SessionKeyCaps? = null,
) {
    val projects = controllers.projects
    val missionControl = controllers.missionControl
    val attacher = terminal.attacher
    val mode = terminal.mode
    LaunchedEffect(projects, missionControl) {
        missionControl.view.filterNotNull().collect { projects.onIntent(ProjectsIntent.ClearSelection) }
    }
    val windowMode by (mode?.mode ?: LIST_ONLY).collectAsState()
    val terminalMode = mode != null && windowMode == WindowMode.TERMINAL
    Column(modifier = modifier.fillMaxSize()) {
        val crumbs = rememberCrumbs(missionControl, terminal.terminalKey, terminalMode)
        WindowTopBar(controllers, chrome, crumbs) { mode?.onEvent(WindowModeEvent.Back) }
        // One movable panel, so the terminal widget survives the switch of the layout (AM-809, risks).
        val panel = remember(terminal.panel) { terminal.panel?.let(::movablePanel) }
        Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
            if (mode != null && terminalMode) {
                TerminalModeLayout(
                    Modifier.weight(weight = 1f),
                    TerminalModeParts(projects, missionControl, mode, panel, terminal.terminalKey, attacher),
                )
            } else {
                ListLayout(projects, missionControl, handlersOf(attacher, keyCaps), panel, mode)
            }
        }
        WindowStatusBar(controllers, chrome, terminalMode)
        CommandPalette(controllers.palette)
    }
}

private fun handlersOf(
    attacher: SessionAttacher?,
    keyCaps: SessionKeyCaps?,
): MissionControlHandlers =
    MissionControlHandlers(
        onAttach = { attacher?.attachSelected() },
        onReply = { attacher?.replySelected() },
        keyCaps = keyCaps,
    )

private val LIST_ONLY: StateFlow<WindowMode> = MutableStateFlow(WindowMode.LIST)

private fun movablePanel(
    panel: @Composable (Modifier, (() -> Unit)?) -> Unit,
): @Composable (Modifier, (() -> Unit)?) -> Unit =
    movableContentOf { modifier: Modifier, onFullScreen: (() -> Unit)? -> panel(modifier, onFullScreen) }

/** The list mode: the sidebar with the global views, Mission Control and the terminal panel under it. */
@Composable
private fun RowScope.ListLayout(
    projects: ProjectsController,
    missionControl: MissionControlController,
    handlers: MissionControlHandlers,
    panel: (@Composable (Modifier, (() -> Unit)?) -> Unit)?,
    mode: WindowModeState?,
) {
    Column {
        ProjectsSidebar(projects, Modifier.weight(weight = 1f), keyCapOf(AppShortcuts.ADD_PROJECT))
        GlobalViews(missionControl)
        ShortcutsHelp(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
    Column(modifier = Modifier.weight(weight = 1f)) {
        MissionControl(missionControl, Modifier.weight(weight = 1f).fillMaxWidth(), handlers = handlers)
        panel?.invoke(
            Modifier.fillMaxWidth().height(TERMINAL_HEIGHT),
            mode?.let { { it.onEvent(WindowModeEvent.FullScreen) } },
        )
    }
}

/** The height of the terminal panel under Mission Control. */
private val TERMINAL_HEIGHT = 320.dp
