package dev.agentmanager.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.swing.JComponent

private val embeddedPaneContent: @Composable (JComponent) -> Unit = { EmbeddedWidget(it) }

/**
 * The tab bar and the panes; [onAppKey] gets ⌘ keys pressed in a terminal that are not terminal shortcuts.
 * Draws nothing while there are no tabs and no error. [paneContent] embeds the widget of a tab (null: `SwingPanel`);
 * tests replace it with a plain marker.
 */
@Composable
public fun TerminalArea(
    controller: TerminalController,
    onAppKey: (TerminalKey) -> Boolean,
    modifier: Modifier = Modifier,
    paneContent: (@Composable (JComponent) -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val statuses by controller.statuses.collectAsState()
    if (state.tabs.isEmpty() && state.error == null) return
    TerminalFocus(controller, onAppKey)
    Column(modifier = modifier.testTag(TerminalTags.AREA).background(color = TerminalColors.background)) {
        TerminalTabBar(TabGroups.of(state.tabs), state.activeTab(), statuses, controller)
        state.error?.let { ErrorBar(it, onDismiss = controller::dismissError) }
        Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
            TerminalPanes(state, controller.search, paneContent ?: embeddedPaneContent)
        }
    }
}

/** The tab shown in the active pane. */
private fun TerminalUiState.activeTab(): TabId? = layout?.leaves()?.firstOrNull { it.pane == active }?.tab
