package dev.agentmanager.terminal.ui

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.debug
import dev.agentmanager.terminal.api.SearchDirection
import dev.agentmanager.terminal.api.SearchError
import dev.agentmanager.terminal.api.SearchQuery
import dev.agentmanager.terminal.api.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The search bar of the active pane (FR-T3). [activeTab] gives the tab shown in the active pane. Runs searches
 * in [scope] (the window scope, Main dispatcher). Logs tab id, mode and hit count only, never the query (ENG-C8).
 */
internal class TerminalSearchController(
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val activeTab: () -> TerminalTab?,
) {
    private val bar = MutableStateFlow<SearchBarState?>(null)
    private var tab: TerminalTab? = null
    private var finding: Job? = null
    private var stepping: Job? = null
    private var focusRequests = 0
    private var generation = 0

    /** The open bar, null when closed. */
    val state: StateFlow<SearchBarState?> = bar

    /** Opens the bar for the active tab, or asks an open bar for the focus again. */
    fun open() {
        val current = bar.value
        if (current != null) {
            bar.value = current.copy(focusRequest = ++focusRequests)
            return
        }
        val target = activeTab() ?: return
        tab = target
        bar.value =
            SearchBarState(
                tab = target.id,
                query = SearchQuery(""),
                result = SearchResult.NONE,
                invalid = false,
                focusRequest = ++focusRequests,
            )
    }

    /** Closes the bar and clears the highlights of its tab. */
    fun close() {
        val closing = tab
        generation++
        finding?.cancel()
        stepping?.cancel()
        finding = null
        stepping = null
        tab = null
        bar.value = null
        if (closing != null) scope.launch { closing.session.search.clear() }
    }

    /** The text field changed; runs a new search (empty text clears). */
    fun type(text: String): Unit = rerun { it.copy(text = text) }

    /** Aa: match case on or off. */
    fun toggleCase(): Unit = rerun { it.copy(matchCase = !it.matchCase) }

    /** .*: regex on or off. */
    fun toggleRegex(): Unit = rerun { it.copy(regex = !it.regex) }

    /** Up arrow or Enter: [SearchDirection.UP]; down arrow or Shift Enter: [SearchDirection.DOWN]. */
    fun step(direction: SearchDirection) {
        val target = tab ?: return
        // Queued behind a running find (and earlier steps), so it moves within the new hits and counts them.
        val waiting = listOfNotNull(finding, stepping)
        val queuedAt = generation
        stepping =
            scope.launch {
                waiting.forEach { it.join() }
                // A rerun or close since queuing made the hits stale; earlier steps in the chain are not cancelled.
                if (generation != queuedAt || (bar.value?.result?.count ?: 0) == 0) return@launch
                val stepped = target.session.search.step(direction)
                if (stepped is Outcome.Ok) publish(stepped.value, invalid = false)
            }
    }

    /** The tab of the active pane changed (or was closed: null); a bar of another tab closes. */
    fun onActiveTabChanged(shown: TerminalTab?) {
        val current = bar.value ?: return
        if (shown?.id != current.tab) close()
    }

    private fun rerun(change: (SearchQuery) -> SearchQuery) {
        val current = bar.value ?: return
        val target = tab ?: return
        val query = change(current.query)
        bar.value = current.copy(query = query)
        generation++
        finding?.cancel()
        stepping?.cancel()
        stepping = null
        finding = scope.launch { search(target, query) }
    }

    private suspend fun search(
        target: TerminalTab,
        query: SearchQuery,
    ) {
        val session = target.session.search
        if (query.text.isEmpty()) {
            session.clear()
            publish(SearchResult.NONE, invalid = false)
            return
        }
        when (val found = session.find(query)) {
            is Outcome.Ok -> {
                publish(found.value, invalid = false)
                logger.debug(
                    "terminal search",
                    "tabId" to target.id.value,
                    "regex" to query.regex,
                    "count" to found.value.count,
                )
            }

            is Outcome.Err -> {
                publish(SearchResult.NONE, invalid = found.error == SearchError.InvalidPattern)
            }
        }
    }

    private fun publish(
        result: SearchResult,
        invalid: Boolean,
    ) {
        bar.update { it?.copy(result = result, invalid = invalid) }
    }
}
