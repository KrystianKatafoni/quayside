package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome

/** What to look for. [text] empty = no search. [regex]: Java regex syntax; [matchCase] applies to both modes. */
public data class SearchQuery(
    val text: String,
    val matchCase: Boolean = false,
    val regex: Boolean = false,
)

/** A hit in row [row] (0 = oldest scrollback line of the snapshot), columns [start] until [end] (exclusive). */
public data class SearchMatch(
    val row: Int,
    val start: Int,
    val end: Int,
)

/**
 * Hits found: [count]; [current] is the 0-based index of the selected hit in reading order, null when none.
 * [limited]: stopped at [ScrollbackMatcher.MAX_MATCHES].
 */
public data class SearchResult(
    val count: Int,
    val current: Int?,
    val limited: Boolean,
) {
    public companion object {
        public val NONE: SearchResult = SearchResult(count = 0, current = null, limited = false)
    }
}

/** [UP]: towards older output; [DOWN]: towards newer output. Both wrap around. */
public enum class SearchDirection { UP, DOWN }

/** Why a search did not run. Never carries the pattern or exception text (ENG-C8). */
public sealed interface SearchError {
    /** [SearchQuery.regex] is set and [SearchQuery.text] is not a valid pattern. */
    public data object InvalidPattern : SearchError

    /** The session was closed. */
    public data object Closed : SearchError
}

/**
 * Find in one terminal (FR-T3). [find] highlights every hit, selects the newest one (closest to the bottom) and
 * scrolls it into view. Any dispatcher; matching runs off the Swing thread, highlights are drawn on it.
 * The hits are a snapshot: new output does not update them until the next [find].
 */
public interface TerminalSearch {
    /**
     * Replaces the previous search. Empty text clears and returns [SearchResult.NONE]. An invalid pattern returns
     * Err([SearchError.InvalidPattern]) and also clears the previous highlights, so a failed search shows nothing.
     */
    public suspend fun find(query: SearchQuery): Outcome<SearchResult, SearchError>

    /** Selects the next hit in [direction] and scrolls to it; [SearchResult.NONE] without hits. */
    public suspend fun step(direction: SearchDirection): Outcome<SearchResult, SearchError>

    /** Removes highlights and forgets the hits. Idempotent; harmless after close. */
    public suspend fun clear()
}
