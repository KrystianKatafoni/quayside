package dev.agentmanager.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuScope
import dev.agentmanager.commandpalette.ui.toKeyShortcut
import dev.agentmanager.terminal.ui.SplitAxis
import dev.agentmanager.terminal.ui.TerminalController
import dev.agentmanager.terminal.ui.TerminalProject

private val NEXT_PANE_KEY = KeyShortcut(Key.DirectionRight, meta = true, alt = true)
private val PREVIOUS_PANE_KEY = KeyShortcut(Key.DirectionLeft, meta = true, alt = true)

/**
 * Items of the "Terminal" menu (AM-503, decision 9). [project]: the project selected in the sidebar, where a new
 * shell starts. [blocked]: the palette or a dialog has the keys, so every item is off.
 */
@Composable
internal fun MenuScope.TerminalItems(
    terminal: TerminalController,
    project: TerminalProject?,
    blocked: Boolean,
) {
    val state by terminal.menu.collectAsState()
    val on = !blocked
    val newShell = AppShortcuts.NEW_SHELL.toKeyShortcut()
    Entry("command.newShell", project != null && on, newShell) { project?.let(terminal::openShell) }
    val sideBySide = AppShortcuts.SPLIT_SIDE_BY_SIDE.toKeyShortcut()
    Entry("command.splitSideBySide", state.canSplit && on, sideBySide) { terminal.split(SplitAxis.SIDE_BY_SIDE) }
    val stacked = AppShortcuts.SPLIT_STACKED.toKeyShortcut()
    Entry("command.splitStacked", state.canSplit && on, stacked) { terminal.split(SplitAxis.STACKED) }
    Separator()
    Entry("command.nextPane", state.canSwitchPane && on, NEXT_PANE_KEY) { terminal.focusNext() }
    Entry("command.previousPane", state.canSwitchPane && on, PREVIOUS_PANE_KEY) { terminal.focusPrevious() }
    val maximize = if (state.maximized) "command.restorePanes" else "command.maximizePane"
    Entry(
        maximize,
        state.canSwitchPane && on,
        AppShortcuts.MAXIMIZE_PANE.toKeyShortcut(),
    ) { terminal.toggleMaximized() }
    Separator()
    Entry("command.closeTerminalTab", state.hasTabs && on) { terminal.closeActive() }
}

@Composable
private fun MenuScope.Entry(
    textKey: String,
    enabled: Boolean,
    shortcut: KeyShortcut? = null,
    onClick: () -> Unit,
) {
    Item(text = Strings[textKey], enabled = enabled, shortcut = shortcut, onClick = onClick)
}
