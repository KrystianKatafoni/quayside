package dev.quayside.summaries.impl

import dev.quayside.summaries.api.Summarizer
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The rules of `LastMessageSummary.of` from the AM-604 plan ("Reguły A" 1-6). */
class LastMessageSummaryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `cleans a message down to a short summary`(
        case: String,
        message: String,
        expected: String?,
    ) {
        val summary = LastMessageSummary.of(message)

        withClue(case) {
            summary shouldBe expected
            if (summary != null) {
                summary.codePointCount(0, summary.length) shouldBeLessThanOrEqual Summarizer.MAX_LENGTH
                summary.any { it == '\n' || it == '\r' } shouldBe false
            }
        }
    }

    companion object {
        private const val FENCE = "```"
        private const val ELLIPSIS = "…"

        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): List<Arguments> =
            listOf(
                arguments("one sentence", "Fixed the login form.", "Fixed the login form."),
                arguments("two sentences", "Gotowe! Dodałem test.", "Gotowe! Dodałem test."),
                arguments("three sentences become two", "A one. B two. C three.", "A one. B two."),
                arguments("question and ellipsis end a sentence", "Wait… Really? Yes.", "Wait… Really?"),
                arguments("first paragraph only", "First.\n\nSecond.", "First."),
                arguments("heading line is dropped", "## Summary\n\nFixed it.", "Fixed it."),
                arguments("list items are joined", "Done:\n- fixed A\n- added B", "Done: fixed A added B"),
                arguments("ordered list markers", "1. First\n2) Second", "First Second"),
                arguments("checkbox markers", "- [x] done item\n- [ ] open item", "done item open item"),
                arguments("quote marker", "> quoted text.", "quoted text."),
                arguments("table row is dropped", "| a | b |\n\nOk.", "Ok."),
                arguments("horizontal rule is dropped", "Note\n---\nReal.", "Note Real."),
                arguments("star rule is dropped", "Note\n***\nReal.", "Note Real."),
                arguments("only a code block", "${FENCE}kotlin\nval x = 1\n$FENCE", null),
                arguments("code before the text", "$FENCE\ncode\n$FENCE\nShipped.", "Shipped."),
                arguments("tilde fence", "~~~\ncode\n~~~\nAfter.", "After."),
                arguments(
                    "a backtick fence is not closed by tildes",
                    "Done.\n$FENCE\n~~~\ncode\n~~~\n$FENCE\nAfter.",
                    "Done. After.",
                ),
                arguments(
                    "a tilde fence is not closed by backticks",
                    "Done.\n~~~\n$FENCE\ncode\n$FENCE\n~~~\nAfter.",
                    "Done. After.",
                ),
                arguments("unclosed fence drops the rest", "Done.\n$FENCE\nrest", "Done."),
                arguments(
                    "inline markup",
                    "Use `gradle` and **[docs](http://x)**",
                    "Use gradle and docs",
                ),
                arguments("double underscore bold", "__bold__ text.", "bold text."),
                arguments("snake case stays", "Renamed user_id.", "Renamed user_id."),
                arguments("single star stays", "Use *args here.", "Use *args here."),
                arguments(
                    "version number is no sentence end",
                    "Bumped to 2.1.139 today. Next.",
                    "Bumped to 2.1.139 today. Next.",
                ),
                arguments("CRLF and control characters", "Done\r\nnow.\u0007", "Done now."),
                arguments("blank spaces collapse", "  Done   now.  ", "Done now."),
                arguments("empty", "", null),
                arguments("only whitespace", "  \n ", null),
            ) + longCases() + boundaryCases()

        private fun boundaryCases(): List<Arguments> {
            val exactly160 = "a".repeat(79) + ". " + "b".repeat(78) + "."
            return listOf(
                arguments(
                    "160 code points with a surrogate pair fit",
                    "a".repeat(158) + "😀😀",
                    "a".repeat(158) + "😀😀",
                ),
                arguments(
                    "a cut never splits a surrogate pair",
                    "a".repeat(158) + "😀😀😀",
                    "a".repeat(158) + "😀" + ELLIPSIS,
                ),
                arguments("two sentences of exactly 160 code points fit", exactly160, exactly160),
                arguments(
                    "the half threshold counts code points, not UTF-16 chars",
                    "😀".repeat(45) + " " + "b".repeat(160),
                    "😀".repeat(45) + " " + "b".repeat(113) + ELLIPSIS,
                ),
            )
        }

        private fun longCases(): List<Arguments> {
            val words = "abcdef ".repeat(29).trim()
            val cut = "abcdef ".repeat(22).trim()
            val firstOnly = "x".repeat(99) + "."
            return listOf(
                arguments("long sentence is cut on a space", words, cut + ELLIPSIS),
                arguments("no spaces is a hard cut", "a".repeat(200), "a".repeat(159) + ELLIPSIS),
                arguments(
                    "a space in the first half does not count",
                    "a".repeat(30) + " " + "b".repeat(170),
                    "a".repeat(30) + " " + "b".repeat(128) + ELLIPSIS,
                ),
                arguments("second sentence that does not fit is dropped", "$firstOnly ${"y".repeat(99)}.", firstOnly),
            )
        }
    }
}
