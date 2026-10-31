package dev.agentmanager.terminal.ui

/** Reducer steps for tabs: opening, showing, closing (plan AM-503, table 1). */
internal object TerminalTabs {
    fun reduce(
        state: TerminalUiState,
        event: TerminalEvent.OfTab,
    ): TerminalUiState =
        when (event) {
            is TerminalEvent.Opened -> open(state, event.tab)
            is TerminalEvent.Replaced -> replace(state, event.old, event.tab)
            is TerminalEvent.Select -> select(state, event.tab)
            is TerminalEvent.Close -> close(state, event.tab)
            is TerminalEvent.Exited -> state.copy(tabs = state.tabs.map { it.exited(event) })
        }

    /** A new tab takes the active pane; the first tab creates the first pane. */
    fun open(
        state: TerminalUiState,
        tab: TerminalTab,
    ): TerminalUiState {
        val tabs = state.tabs + tab
        val active = state.active
        val layout = state.layout
        return if (layout == null || active == null) {
            state.copy(tabs = tabs, layout = PaneTree.Leaf(FIRST_PANE, tab.id), active = FIRST_PANE)
        } else {
            state.copy(tabs = tabs, layout = layout.replace(active, PaneTree.Leaf(active, tab.id)))
        }
    }

    /** [tab] takes the place of [old] in the list and in its pane, or in the active pane when [old] is hidden. */
    fun replace(
        state: TerminalUiState,
        old: TabId,
        tab: TerminalTab,
    ): TerminalUiState {
        val layout = state.layout
        val active = state.active
        if (state.tabs.none { it.id == old } || layout == null || active == null) return open(state, tab)
        val tabs = state.tabs.map { if (it.id == old) tab else it }
        val pane = layout.leafOf(old)?.pane ?: active
        return state.copy(tabs = tabs, layout = layout.replace(pane, PaneTree.Leaf(pane, tab.id)), active = pane)
    }

    /** A live tab of [key] is focused, else an exited one is replaced, else a new tab opens. */
    fun reuseOf(
        tabs: List<TerminalTab>,
        key: TabKey,
    ): TabReuse {
        val keyed = tabs.filter { it.key == key }
        val found = keyed.firstOrNull { it.exitCode == null }
        return when {
            found != null -> TabReuse.Focus(found.id)
            keyed.isNotEmpty() -> TabReuse.Replace(keyed.first().id)
            else -> TabReuse.Open
        }
    }

    /** Shows [tab] where it already is, or in the active pane when it is hidden. */
    private fun select(
        state: TerminalUiState,
        tab: TabId,
    ): TerminalUiState {
        val layout = state.layout
        val active = state.active
        if (state.tabs.none { it.id == tab } || layout == null || active == null) return state
        val shown = layout.leafOf(tab)
        return if (shown != null) {
            state.copy(active = shown.pane)
        } else {
            state.copy(layout = layout.replace(active, PaneTree.Leaf(active, tab)))
        }
    }

    /** Removes [tab]; its pane goes away, or with one pane another tab takes its place. */
    private fun close(
        state: TerminalUiState,
        tab: TabId,
    ): TerminalUiState {
        val remaining = state.copy(tabs = state.tabs.filterNot { it.id == tab })
        val layout = state.layout
        val leaf = layout?.leafOf(tab)
        return when {
            state.tabs.none { it.id == tab } -> state
            layout == null || leaf == null -> remaining
            layout.leaves().size > 1 -> PaneLayout.removePane(remaining, layout, leaf.pane)
            else -> showOnly(remaining, leaf.pane, successor(state.tabs, tab))
        }
    }

    private fun showOnly(
        remaining: TerminalUiState,
        pane: PaneId,
        tab: TabId?,
    ): TerminalUiState =
        if (tab == null) {
            remaining.copy(layout = null, active = null, maximized = false)
        } else {
            remaining.copy(layout = PaneTree.Leaf(pane, tab))
        }

    /** The tab to show in place of the closed [tab]: neighbour in its group, else the last tab of another group. */
    private fun successor(
        tabs: List<TerminalTab>,
        tab: TabId,
    ): TabId? {
        val groups = TabGroups.of(tabs)
        val group = groups.first { g -> g.tabs.any { it.id == tab } }
        val at = group.tabs.indexOfFirst { it.id == tab }
        val neighbour = group.tabs.getOrNull(at + 1) ?: group.tabs.getOrNull(at - 1)
        val other = groups.lastOrNull { it !== group }?.tabs?.last()
        return (neighbour ?: other)?.id
    }

    private fun TerminalTab.exited(event: TerminalEvent.Exited): TerminalTab =
        if (id == event.tab) copy(exitCode = event.code) else this

    private val FIRST_PANE = PaneId(1)
}
