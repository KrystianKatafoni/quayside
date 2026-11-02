package dev.quayside.summaries.impl

import dev.quayside.summaries.api.Summarizer

/**
 * Pure core of the summary without a model (AM-604 plan, "Reguły A" 1-6): Markdown is stripped, the first paragraph is
 * kept and shortened to at most two sentences within [Summarizer.MAX_LENGTH] code points.
 */
internal object LastMessageSummary {
    private const val MAX = Summarizer.MAX_LENGTH
    private const val ELLIPSIS = "…"
    private const val SENTENCE_ENDS = ".!?…"
    private val FENCES = listOf("```", "~~~")
    private val DROPPED = Regex("""^(#{1,6}(\s|$)|\||-{3,}$|\*{3,}$)""")
    private val LINE_MARKERS = Regex("""^(?:>\s*|(?:[-*+]|\d+[.)])\s+|\[[ xX]](?=\s|$)\s*)+""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val EMPHASIS = Regex("""\*\*|__|`""")
    private val SPACES = Regex("""\s+""")

    /** The summary of [message], or null when nothing is left after cleaning. */
    fun of(message: String): String? {
        val text = SPACES.replace(firstParagraph(message.lines()).joinToString(" "), " ").trim()
        return if (text.isEmpty()) null else shorten(text)
    }

    private fun firstParagraph(lines: List<String>): List<String> {
        val paragraph = mutableListOf<String>()
        var fence: String? = null
        for (raw in lines) {
            val line = blankControls(raw).trim()
            val opener = FENCES.firstOrNull(line::startsWith)
            when {
                fence != null -> if (line.startsWith(fence)) fence = null
                opener != null -> fence = opener
                line.isEmpty() -> if (paragraph.isNotEmpty()) break
                else -> cleanLine(line)?.let(paragraph::add)
            }
        }
        return paragraph
    }

    private fun blankControls(line: String): String =
        line.map { if (Character.getType(it) == Character.CONTROL.toInt()) ' ' else it }.joinToString("")

    private fun cleanLine(line: String): String? =
        if (DROPPED.containsMatchIn(line)) {
            null
        } else {
            EMPHASIS.replace(LINK.replace(LINE_MARKERS.replace(line, ""), "\$1"), "").ifBlank { null }
        }

    private fun shorten(text: String): String {
        val first = text.substring(0, sentenceEnd(text, 0))
        if (first.codePointCount(0, first.length) > MAX) return cut(first)
        val two = text.substring(0, sentenceEnd(text, first.length))
        return if (two.codePointCount(0, two.length) <= MAX) two else first
    }

    /** The index after the first sentence that ends at or after [from] (the end of [text] when none does). */
    private fun sentenceEnd(
        text: String,
        from: Int,
    ): Int {
        for (i in from until text.length) {
            val atEnd = i + 1 == text.length || text[i + 1].isWhitespace()
            if (text[i] in SENTENCE_ENDS && atEnd) return i + 1
        }
        return text.length
    }

    private fun cut(sentence: String): String {
        val prefix = sentence.substring(0, sentence.offsetByCodePoints(0, MAX - 1))
        val space = prefix.lastIndexOf(' ')
        val keep = if (prefix.codePointCount(0, maxOf(space, 0)) > MAX / 2) prefix.substring(0, space) else prefix
        return keep.trimEnd() + ELLIPSIS
    }
}
