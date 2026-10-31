package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Pure matching shared by `impl` and fakes: per row, left to right, non-overlapping, empty hits skipped. */
public object ScrollbackMatcher {
    /** Upper bound of hits returned by [find]; a search that reaches it is reported as limited. */
    public const val MAX_MATCHES: Int = 10_000

    /**
     * All hits of [query] in [rows], at most [MAX_MATCHES]; empty text gives no hits.
     * Err([SearchError.InvalidPattern]) when [SearchQuery.regex] is set and the text is not a valid pattern.
     */
    public fun find(
        rows: List<String>,
        query: SearchQuery,
    ): Outcome<List<SearchMatch>, SearchError> {
        val pattern = if (query.text.isEmpty()) null else compile(query)
        return when {
            query.text.isEmpty() -> Outcome.Ok(emptyList())
            pattern == null -> Outcome.Err(SearchError.InvalidPattern)
            else -> Outcome.Ok(collect(rows, pattern))
        }
    }

    private fun collect(
        rows: List<String>,
        pattern: Pattern,
    ): List<SearchMatch> {
        val hits = ArrayList<SearchMatch>()
        for ((row, text) in rows.withIndex()) {
            val matcher = pattern.matcher(text)
            while (hits.size < MAX_MATCHES && matcher.find()) {
                if (matcher.end() > matcher.start()) hits += SearchMatch(row, matcher.start(), matcher.end())
            }
            if (hits.size >= MAX_MATCHES) break
        }
        return hits
    }

    private fun compile(query: SearchQuery): Pattern? {
        val flags = if (query.matchCase) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return try {
            if (query.regex) Pattern.compile(query.text, flags) else Pattern.compile(Pattern.quote(query.text), flags)
        } catch (_: PatternSyntaxException) {
            null
        }
    }
}
