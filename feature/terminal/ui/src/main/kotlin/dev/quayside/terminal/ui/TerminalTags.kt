package dev.quayside.terminal.ui

import dev.quayside.core.domain.ProjectId

/** Semantics tags of the terminal area for UI tests. A pane frame exposes `selected` = it is the active pane. */
internal object TerminalTags {
    const val AREA = "terminal.area"
    const val ERROR = "terminal.error"
    const val ERROR_DISMISS = "terminal.error.dismiss"

    /** The search bar of the active pane and its parts (AM-504). */
    const val SEARCH_BAR = "terminal.search"
    const val SEARCH_FIELD = "terminal.search.field"

    /** The `3/12` counter; absent while the text is empty, invalid or has no hits. */
    const val SEARCH_COUNTER = "terminal.search.counter"

    /** The fixed "no hits" text. */
    const val SEARCH_NO_HITS = "terminal.search.noHits"

    /** The fixed "invalid pattern" text. */
    const val SEARCH_INVALID = "terminal.search.invalid"
    const val SEARCH_CASE = "terminal.search.case"
    const val SEARCH_REGEX = "terminal.search.regex"
    const val SEARCH_UP = "terminal.search.up"
    const val SEARCH_DOWN = "terminal.search.down"
    const val SEARCH_CLOSE = "terminal.search.close"

    fun group(project: ProjectId): String = "terminal.group.${project.value}"

    /** The "+" at the end of a group: opens a shell in that project. */
    fun groupAdd(project: ProjectId): String = "terminal.group.${project.value}.add"

    fun tab(tab: TabId): String = "terminal.tab.${tab.value}"

    fun tabClose(tab: TabId): String = "terminal.tab.${tab.value}.close"

    /** The `exit N` marker of an exited tab. */
    fun tabExit(tab: TabId): String = "terminal.tab.${tab.value}.exit"

    /** The status dot of a keyed tab (AM-510); its `stateDescription` is the text of the status. */
    fun tabStatus(tab: TabId): String = "terminal.tab.${tab.value}.status"

    fun pane(pane: PaneId): String = "terminal.pane.${pane.value}"
}
