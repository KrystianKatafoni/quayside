package dev.quayside.terminal.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalError
import dev.quayside.terminal.api.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tabs and panes of embedded terminals grouped by project (FR-T2). Opens sessions through [terminals] in [scope]
 * (the window scope, Main dispatcher; not thread-safe). [shellLabel] names shell tabs, e.g. `zsh`. Logs tab and
 * pane ids, kinds and fixed failure categories only, never commands, paths or project names (ENG-C8).
 *
 * The 13 operations are the API of the panel, the menu and the key dispatcher; the logic is in the reducers.
 */
@Suppress("TooManyFunctions")
public class TerminalController(
    private val terminals: TerminalApi,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val shellLabel: String,
) {
    private val store = MutableStateFlow(TerminalUiState())
    private val menuState = MutableStateFlow(MENU_EMPTY)
    private val exitWatchers = mutableMapOf<TabId, Job>()
    private var lastTab = 0
    private var splitPending = false
    private val opening = mutableSetOf<TabKey>()

    internal val state: StateFlow<TerminalUiState> = store

    private val statusStore = MutableStateFlow(emptyMap<TabKey, TabStatus>())
    private val focusStore = MutableStateFlow(0)

    /** Status dots by tab key (AM-510). */
    internal val statuses: StateFlow<Map<TabKey, TabStatus>> = statusStore

    /** Counts requests to give the widget the focus again (AM-510). */
    internal val focusRequests: StateFlow<Int> = focusStore

    /** The search bar of the active pane (AM-504). */
    internal val search = TerminalSearchController(scope, logger) { activeTab() }

    /** What the terminal can do now; `app` enables menu items and palette commands from it. */
    public val menu: StateFlow<TerminalMenuState> = menuState

    /** Opens a login shell in [project]'s dir as a new tab shown in the active pane. Failures show in the panel. */
    public fun openShell(project: TerminalProject) {
        open(TabSpec(project, shellLabel, TabKind.SHELL), Placement.Active) { terminals.openShell(project.dir) }
    }

    /** Runs [command] (e.g. `attachCommand`) in a new tab labelled [label], shown in the active pane (AM-505). */
    public fun openCommand(
        project: TerminalProject,
        label: String,
        command: ShellCommand,
    ) {
        open(TabSpec(project, label, TabKind.COMMAND), Placement.Active) { terminals.openCommand(command) }
    }

    /**
     * Activates the tab opened with [key] and gives its widget the focus; starts no process. Without such a tab,
     * runs [command] in a new tab labelled [label] in [project]'s group, shown in the active pane. A tab of [key]
     * whose process exited is replaced in place by a new one. Ignored while a tab of [key] is still opening.
     */
    public fun focusOrOpen(
        project: TerminalProject,
        key: TabKey,
        label: String,
        command: ShellCommand,
    ) {
        if (key in opening) return
        val spec = TabSpec(project, label, TabKind.COMMAND, key)
        when (val reuse = TerminalTabs.reuseOf(store.value.tabs, key)) {
            is TabReuse.Focus -> focus(reuse.tab)
            is TabReuse.Replace -> start(spec, Placement.Replace(reuse.tab), command)
            TabReuse.Open -> start(spec, Placement.Active, command)
        }
    }

    /** Shows a dot next to every tab whose key is in [statuses]; other tabs show none. Replaces the previous map. */
    public fun showStatuses(statuses: Map<TabKey, TabStatus>) {
        statusStore.value = statuses
    }

    /** Closes the tab of the active pane and its session. */
    public fun closeActive() {
        val current = store.value
        val pane = current.active ?: return
        current.layout
            ?.leaves()
            ?.firstOrNull { it.pane == pane }
            ?.let { close(it.tab) }
    }

    /** Opens a shell of the active tab's project in a new pane next to the active one; ignored unless canSplit. */
    public fun split(axis: SplitAxis) {
        val project = activeTab()?.project
        if (project == null || !menuState.value.canSplit) return
        splitPending = true
        publishMenu()
        val spec = TabSpec(project, shellLabel, TabKind.SHELL)
        open(spec, Placement.Split(axis)) { terminals.openShell(project.dir) }
    }

    /** Activates the next pane in reading order, cyclic. */
    public fun focusNext(): Unit = dispatch(TerminalEvent.FocusNext)

    /** Activates the previous pane in reading order, cyclic. */
    public fun focusPrevious(): Unit = dispatch(TerminalEvent.FocusPrevious)

    /** Shows only the active pane, or brings the layout back. Ignored with one pane. */
    public fun toggleMaximized(): Unit = dispatch(TerminalEvent.ToggleMaximized)

    /** A tab was clicked in the bar: shows it in its pane, or in the active pane when hidden. */
    internal fun select(tab: TabId): Unit = dispatch(TerminalEvent.Select(tab))

    /** The x on a tab: closes that tab and its session. */
    internal fun close(tab: TabId) {
        val closing = store.value.tabs.firstOrNull { it.id == tab } ?: return
        exitWatchers.remove(tab)?.cancel()
        dispatch(TerminalEvent.Close(tab))
        closing.session.close()
        logger.info("terminal tab closed", "tabId" to tab.value)
    }

    /** The user clicked a pane (focus moved into its widget). */
    internal fun focusPane(pane: PaneId): Unit = dispatch(TerminalEvent.FocusPane(pane))

    /** The x on the error bar. */
    internal fun dismissError(): Unit = dispatch(TerminalEvent.DismissError)

    private fun activeTab(): TerminalTab? {
        val current = store.value
        val leaf = current.layout?.leaves()?.firstOrNull { it.pane == current.active }
        return current.tabs.firstOrNull { it.id == leaf?.tab }
    }

    private fun focus(tab: TabId) {
        dispatch(TerminalEvent.Select(tab))
        focusStore.value += 1
        logger.info("terminal tab focused", "tabId" to tab.value)
    }

    private fun start(
        spec: TabSpec,
        placement: Placement,
        command: ShellCommand,
    ) = open(spec, placement) { terminals.openCommand(command) }

    private fun open(
        spec: TabSpec,
        placement: Placement,
        start: suspend () -> Outcome<TerminalSession, TerminalError>,
    ) {
        spec.key?.let { opening += it }
        scope.launch {
            try {
                when (val result = start()) {
                    is Outcome.Ok -> opened(newTab(spec, result.value), placement)
                    is Outcome.Err -> failed(result.error)
                }
            } finally {
                spec.key?.let { opening -= it }
                if (placement is Placement.Split) {
                    splitPending = false
                    publishMenu()
                }
            }
        }
    }

    private fun newTab(
        spec: TabSpec,
        session: TerminalSession,
    ): TerminalTab =
        TerminalTab(TabId(++lastTab), spec.project, spec.label, spec.kind, session, exitCode = null, key = spec.key)

    private fun opened(
        tab: TerminalTab,
        placement: Placement,
    ) {
        val replaced = (placement as? Placement.Replace)?.let { exitedTab(it.old) }
        dispatch(
            when (placement) {
                Placement.Active -> TerminalEvent.Opened(tab)
                is Placement.Split -> TerminalEvent.SplitOpened(tab, placement.axis)
                is Placement.Replace -> TerminalEvent.Replaced(placement.old, tab)
            },
        )
        replaced?.let {
            it.session.close()
        }
        exitWatchers[tab.id] = scope.launch { exited(tab) }
        logger.info("terminal tab opened", "tabId" to tab.id.value, "kind" to tab.kind.name)
    }

    private fun exitedTab(id: TabId): TerminalTab? = store.value.tabs.firstOrNull { it.id == id }

    private suspend fun exited(tab: TerminalTab) {
        val code =
            tab.session.exitCode
                .filterNotNull()
                .first()
        dispatch(TerminalEvent.Exited(tab.id, code))
        exitWatchers.remove(tab.id)
        logger.info("terminal tab exited", "tabId" to tab.id.value, "code" to code)
    }

    private fun failed(error: TerminalError) {
        val failure =
            when (error) {
                TerminalError.WorkDirMissing -> OpenFailure.WORK_DIR_MISSING
                TerminalError.StartFailed -> OpenFailure.START_FAILED
                TerminalError.Closed -> return
            }
        dispatch(TerminalEvent.Failed(failure))
        logger.warn("terminal tab open failed", "reason" to failure.name)
    }

    private fun dispatch(event: TerminalEvent) {
        store.value = TerminalState.reduce(store.value, event)
        publishMenu()
        search.onActiveTabChanged(activeTab())
    }

    private fun publishMenu() {
        val current = store.value
        val panes = current.layout?.leaves()?.size ?: 0
        menuState.value =
            TerminalMenuState(
                hasTabs = current.tabs.isNotEmpty(),
                canSplit = panes in 1 until PaneLayout.MAX_PANES && !splitPending,
                canSwitchPane = panes > 1,
                maximized = current.maximized,
            )
    }

    private companion object {
        val MENU_EMPTY = TerminalMenuState(hasTabs = false, canSplit = false, canSwitchPane = false, maximized = false)
    }
}
