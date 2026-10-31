package dev.agentmanager.commandpalette.api

import java.text.Normalizer

/** Pure search of the palette; shared by `ui`, `impl` and fakes, like `ProjectInputRules`. */
public object CommandMatcher {
    private const val PREFIX = 0
    private const val WORD_START = 1
    private const val SUBSTRING = 2
    private const val SUBSEQUENCE = 3
    private const val NO_MATCH = Int.MAX_VALUE

    private val diacritics = Regex("\\p{M}+")
    private val wordSeparator = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Finds [commands] matching [query] in the title or keywords, ignoring case and Polish diacritics.
     * Order: title prefix, word start, substring, then letter subsequence;
     * ties go by [CommandGroup] and then by entry order. A blank query returns everything in that order.
     */
    public fun search(
        query: String,
        commands: List<Command>,
    ): List<Command> {
        val needle = normalize(query.trim())
        val ranked = commands.map { it to level(needle, it) }
        return ranked
            .filter { it.second <= SUBSEQUENCE }
            .sortedWith(compareBy({ it.second }, { it.first.group.ordinal }))
            .map { it.first }
    }

    private fun level(
        needle: String,
        command: Command,
    ): Int {
        if (needle.isEmpty()) return PREFIX
        val title = levelIn(needle, normalize(command.title), isTitle = true)
        val keywords = command.keywords.map { levelIn(needle, normalize(it), isTitle = false) }
        return (keywords + title).min()
    }

    private fun levelIn(
        needle: String,
        text: String,
        isTitle: Boolean,
    ): Int =
        when {
            isTitle && text.startsWith(needle) -> PREFIX
            text.split(wordSeparator).any { it.startsWith(needle) } -> WORD_START
            text.contains(needle) -> SUBSTRING
            isSubsequence(needle, text) -> SUBSEQUENCE
            else -> NO_MATCH
        }

    private fun isSubsequence(
        needle: String,
        text: String,
    ): Boolean {
        var index = 0
        for (char in text) {
            if (index < needle.length && char == needle[index]) index++
        }
        return index == needle.length
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace('ł', 'l')
}
