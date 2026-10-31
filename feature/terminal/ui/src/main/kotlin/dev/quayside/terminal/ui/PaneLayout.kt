package dev.quayside.terminal.ui

/** Reducer steps for panes: split, close, focus and maximize (plan AM-503, table 2). */
internal object PaneLayout {
    const val MAX_PANES = 4

    fun reduce(
        state: TerminalUiState,
        event: TerminalEvent.OfPane,
    ): TerminalUiState =
        when (event) {
            is TerminalEvent.SplitOpened -> split(state, event.tab, event.axis)
            is TerminalEvent.FocusPane -> focus(state, event.pane)
            TerminalEvent.FocusNext -> step(state, step = 1)
            TerminalEvent.FocusPrevious -> step(state, step = -1)
            TerminalEvent.ToggleMaximized -> toggleMaximized(state)
        }

    /** Splits the active pane for [tab]; with [MAX_PANES] panes the tab replaces the active pane's tab instead. */
    private fun split(
        state: TerminalUiState,
        tab: TerminalTab,
        axis: SplitAxis,
    ): TerminalUiState {
        val layout = state.layout
        val active = state.active
        val leaves = layout?.leaves().orEmpty()
        if (layout == null || active == null || leaves.size >= MAX_PANES) return TerminalTabs.open(state, tab)
        val created = PaneId(leaves.maxOf { it.pane.value } + 1)
        val split = PaneTree.Split(axis, leaves.first { it.pane == active }, PaneTree.Leaf(created, tab.id))
        return state.copy(
            tabs = state.tabs + tab,
            layout = layout.replace(active, split),
            active = created,
            maximized = false,
        )
    }

    /** Drops [pane] from [layout]; the active pane moves to the next one in reading order, else the previous. */
    fun removePane(
        state: TerminalUiState,
        layout: PaneTree,
        pane: PaneId,
    ): TerminalUiState {
        val before = layout.leaves().map { it.pane }
        val rest = layout.without(pane) ?: return state.copy(layout = null, active = null, maximized = false)
        val active =
            if (state.active == pane) {
                val at = before.indexOf(pane)
                before.getOrNull(at + 1) ?: before[at - 1]
            } else {
                state.active
            }
        return state.copy(layout = rest, active = active, maximized = state.maximized && rest.leaves().size > 1)
    }

    /** Activates [pane]; unknown panes are ignored. */
    private fun focus(
        state: TerminalUiState,
        pane: PaneId,
    ): TerminalUiState =
        if (state.layout
                ?.leaves()
                .orEmpty()
                .any { it.pane == pane }
        ) {
            state.copy(active = pane)
        } else {
            state
        }

    /** Activates the pane [step] places after the active one in reading order, cyclic. */
    private fun step(
        state: TerminalUiState,
        step: Int,
    ): TerminalUiState {
        val panes =
            state.layout
                ?.leaves()
                .orEmpty()
                .map { it.pane }
        val at = panes.indexOf(state.active)
        if (panes.size < 2 || at < 0) return state
        return state.copy(active = panes[(at + step + panes.size) % panes.size])
    }

    /** Maximizes the active pane, or brings the layout back; needs at least two panes. */
    private fun toggleMaximized(state: TerminalUiState): TerminalUiState =
        if ((state.layout?.leaves()?.size ?: 0) < 2) state else state.copy(maximized = !state.maximized)
}
