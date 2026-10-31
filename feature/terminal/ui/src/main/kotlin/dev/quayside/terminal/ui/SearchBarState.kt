package dev.quayside.terminal.ui

import dev.quayside.terminal.api.SearchQuery
import dev.quayside.terminal.api.SearchResult

/**
 * The search bar of the pane showing [tab] (plan AM-504, decision 4). [invalid]: the regex does not compile.
 * [focusRequest] grows on every open, so the bar moves the focus to its field again.
 */
internal data class SearchBarState(
    val tab: TabId,
    val query: SearchQuery,
    val result: SearchResult,
    val invalid: Boolean,
    val focusRequest: Int,
)
