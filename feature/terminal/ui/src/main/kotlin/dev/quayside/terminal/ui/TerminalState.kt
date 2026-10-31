package dev.quayside.terminal.ui

/** Pure reducer of tabs and panes (plan AM-503, tables 1 and 2). */
internal object TerminalState {
    fun reduce(
        state: TerminalUiState,
        event: TerminalEvent,
    ): TerminalUiState =
        when (event) {
            is TerminalEvent.OfTab -> TerminalTabs.reduce(state, event)
            is TerminalEvent.OfPane -> PaneLayout.reduce(state, event)
            is TerminalEvent.Failed -> state.copy(error = event.failure)
            TerminalEvent.DismissError -> state.copy(error = null)
        }
}
