package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The matching table of docs/plans/AM-504.md (FR-T3). */
class ScrollbackMatcherTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `matcher finds the hits of the table`(
        @Suppress("UNUSED_PARAMETER") name: String,
        rows: List<String>,
        query: SearchQuery,
        expected: List<SearchMatch>,
    ) {
        ScrollbackMatcher.find(rows, query) shouldBe Outcome.Ok(expected)
    }

    @Test
    fun `invalid pattern returns InvalidPattern`() {
        val result = ScrollbackMatcher.find(listOf("a(b"), SearchQuery("(", regex = true))

        result shouldBe Outcome.Err(SearchError.InvalidPattern)
    }

    @Test
    fun `invalid pattern is fine as literal text`() {
        val result = ScrollbackMatcher.find(listOf("a(b"), SearchQuery("("))

        result shouldBe Outcome.Ok(listOf(SearchMatch(0, 1, 2)))
    }

    @Test
    fun `more hits than the limit are cut at MAX_MATCHES`() {
        val rows = List(ScrollbackMatcher.MAX_MATCHES + 1) { "x" }

        val hits = ScrollbackMatcher.find(rows, SearchQuery("x")).getOrNull().orEmpty()

        hits.size shouldBe ScrollbackMatcher.MAX_MATCHES
        hits.first() shouldBe SearchMatch(0, 0, 1)
        hits.last() shouldBe SearchMatch(ScrollbackMatcher.MAX_MATCHES - 1, 0, 1)
    }

    companion object {
        private fun case(
            name: String,
            rows: List<String>,
            query: SearchQuery,
            vararg expected: SearchMatch,
        ): Arguments = Arguments.of(name, rows, query, expected.toList())

        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): Stream<Arguments> =
            Stream.of(
                case(
                    "ignores case by default",
                    listOf("Error", "no err"),
                    SearchQuery("err"),
                    SearchMatch(0, 0, 3),
                    SearchMatch(1, 3, 6),
                ),
                case(
                    "respects case with matchCase",
                    listOf("Error", "no err"),
                    SearchQuery("err", matchCase = true),
                    SearchMatch(1, 3, 6),
                ),
                case(
                    "hits do not overlap",
                    listOf("aaaa"),
                    SearchQuery("aa"),
                    SearchMatch(0, 0, 2),
                    SearchMatch(0, 2, 4),
                ),
                case(
                    "literal text treats metacharacters literally",
                    listOf("axb a.b"),
                    SearchQuery("a.b"),
                    SearchMatch(0, 4, 7),
                ),
                case(
                    "regex finds pattern hits",
                    listOf("a12b3"),
                    SearchQuery("""\d+""", regex = true),
                    SearchMatch(0, 1, 3),
                    SearchMatch(0, 4, 5),
                ),
                case(
                    "regex ignores case including non-ASCII letters",
                    listOf("ŁÓDŹ"),
                    SearchQuery("łódź", regex = true),
                    SearchMatch(0, 0, 4),
                ),
                case("empty hits are skipped", listOf("ab"), SearchQuery("x*", regex = true)),
                case("empty text gives no hits", listOf("ab", "cd"), SearchQuery("")),
            )
    }
}
