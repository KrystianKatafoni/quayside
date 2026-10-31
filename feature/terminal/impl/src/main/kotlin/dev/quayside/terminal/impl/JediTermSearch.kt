package dev.quayside.terminal.impl

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.ScrollbackMatcher
import dev.quayside.terminal.api.SearchDirection
import dev.quayside.terminal.api.SearchError
import dev.quayside.terminal.api.SearchMatch
import dev.quayside.terminal.api.SearchQuery
import dev.quayside.terminal.api.SearchResult
import dev.quayside.terminal.api.TerminalSearch
import kotlinx.coroutines.withContext

/**
 * [TerminalSearch] over the text buffer of a [SearchableWidget]. The snapshot is taken under the buffer lock and
 * matched on the default dispatcher; hits, selection and highlights change on the main (Swing) dispatcher only,
 * so the state needs no further locking. Hit rows are absolute, which keeps them in place while output grows.
 */
internal class JediTermSearch(
    private val widget: SearchableWidget,
    private val dispatchers: AppDispatchers,
    private val isClosed: () -> Boolean,
) : TerminalSearch {
    private var hits: List<SearchMatch> = emptyList()
    private var index: Int? = null
    private var limited = false

    /**
     * Replaces the previous search. Empty text, or a text that is not a valid pattern, also clears the highlights
     * (an invalid pattern returns [SearchError.InvalidPattern] after clearing).
     */
    override suspend fun find(query: SearchQuery): Outcome<SearchResult, SearchError> =
        when {
            isClosed() -> {
                Outcome.Err(SearchError.Closed)
            }

            query.text.isEmpty() -> {
                clear()
                Outcome.Ok(SearchResult.NONE)
            }

            else -> {
                search(query)
            }
        }

    private suspend fun search(query: SearchQuery): Outcome<SearchResult, SearchError> {
        val found = withContext(dispatchers.default) { ScrollbackMatcher.find(snapshot(), query) }
        return withContext(dispatchers.main) {
            when (found) {
                is Outcome.Err -> {
                    forget()
                    found
                }

                is Outcome.Ok -> {
                    hits = found.value
                    limited = hits.size >= ScrollbackMatcher.MAX_MATCHES
                    select(hits.indices.lastOrNull())
                    Outcome.Ok(result())
                }
            }
        }
    }

    override suspend fun step(direction: SearchDirection): Outcome<SearchResult, SearchError> =
        if (isClosed()) {
            Outcome.Err(SearchError.Closed)
        } else {
            withContext(dispatchers.main) {
                val from = index
                if (from != null) {
                    val delta = if (direction == SearchDirection.UP) -1 else 1
                    select(Math.floorMod(from + delta, hits.size))
                }
                Outcome.Ok(result())
            }
        }

    override suspend fun clear() {
        withContext(dispatchers.main) { forget() }
    }

    /** Text of every row from the oldest scrollback line to the last screen row; empty cells become blanks. */
    private fun snapshot(): List<String> {
        val buffer = widget.terminalTextBuffer
        buffer.lock()
        try {
            return (-buffer.historyLinesCount until buffer.height).map {
                buffer.getLine(it).text.replace('\u0000', ' ')
            }
        } finally {
            buffer.unlock()
        }
    }

    /** Main dispatcher. Marks hit [position] (null = none) as the current one and scrolls it into view. */
    private fun select(position: Int?) {
        index = position
        val selected = position?.let { hits.getOrNull(it) }
        widget.highlighting?.show(hits, selected)
        selected?.let { widget.highlighting?.reveal(it.row) }
    }

    private fun forget() {
        hits = emptyList()
        index = null
        limited = false
        widget.highlighting?.show(emptyList(), null)
    }

    private fun result(): SearchResult =
        if (hits.isEmpty()) SearchResult.NONE else SearchResult(hits.size, index, limited)
}
