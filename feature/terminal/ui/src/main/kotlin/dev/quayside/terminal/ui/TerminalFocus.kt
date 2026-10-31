package dev.quayside.terminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/**
 * Keeps the focus and the keys of the terminal widgets in step with the panes (plan AM-503, decisions 6 and 8):
 * a new active pane gets the focus, a click into a widget makes its pane active, and ⌘ keys of a focused widget
 * go through [TerminalKeyDispatcher].
 */
@Composable
internal fun TerminalFocus(
    controller: TerminalController,
    onAppKey: (TerminalKey) -> Boolean,
) {
    FocusActivePane(controller)
    val appKey by rememberUpdatedState(onAppKey)
    DisposableEffect(controller) {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher =
            TerminalKeyDispatcher(
                components = {
                    controller.state.value.tabs
                        .map { it.session.component() }
                },
                run = controller::perform,
                onAppKey = { appKey(it) },
                focusOwner = { manager.focusOwner },
            )
        val listener = PropertyChangeListener { event -> controller.focusOwnerChanged(event.newValue) }
        manager.addKeyEventDispatcher(dispatcher)
        manager.addPropertyChangeListener("focusOwner", listener)
        onDispose {
            manager.removeKeyEventDispatcher(dispatcher)
            manager.removePropertyChangeListener("focusOwner", listener)
        }
    }
}

/**
 * Gives the focus to the widget of the active pane whenever the active pane or its tab changes, and on every
 * `focusOrOpen` request (AM-510): the shown tab is then active already, but the focus may be in another place.
 */
@Composable
private fun FocusActivePane(controller: TerminalController) {
    val state by controller.state.collectAsState()
    val requests by controller.focusRequests.collectAsState()
    val activeTab =
        state.layout
            ?.leaves()
            ?.firstOrNull { it.pane == state.active }
            ?.tab
    LaunchedEffect(state.active, activeTab, requests) {
        val widget =
            state.tabs
                .firstOrNull { it.id == activeTab }
                ?.session
                ?.component()
        // After this frame: a freshly embedded widget is not in the window yet.
        if (widget != null) SwingUtilities.invokeLater { widget.requestFocusInWindow() }
    }
}

/** Runs the pane command of a key. */
internal fun TerminalController.perform(command: PaneCommand) {
    when (command) {
        is PaneCommand.Split -> split(command.axis)
        PaneCommand.FocusNext -> focusNext()
        PaneCommand.FocusPrevious -> focusPrevious()
        PaneCommand.ToggleMaximized -> toggleMaximized()
        PaneCommand.Find -> search.open()
    }
}

/** The focus moved to [owner]: when it is inside the widget of a shown tab, that tab's pane becomes active. */
private fun TerminalController.focusOwnerChanged(owner: Any?) {
    val current = state.value
    val component = owner as? Component ?: return
    val tab = current.tabs.firstOrNull { SwingUtilities.isDescendingFrom(component, it.session.component()) }
    val leaf = tab?.let { current.layout?.leafOf(it.id) }
    if (leaf != null) focusPane(leaf.pane)
}
