package dev.quayside.terminal.impl.replyspike

/** What the screen asks for. Markers live in [PromptMarkers], calibrated on recorded frames. */
internal sealed interface Prompt {
    data object None : Prompt

    /** Empty input box. */
    data object Text : Prompt

    /** Input box that holds text (a draft or the reply not yet sent). */
    data object TextFilled : Prompt

    /** Spinner row (the session works on the answer). */
    data object Busy : Prompt

    data object Unknown : Prompt

    data class Options(
        val count: Int,
    ) : Prompt

    data class Permission(
        val tool: String?,
    ) : Prompt
}

/**
 * Text markers of the Claude Code TUI, approximated from hand-made frames; step 1 of the measurement protocol
 * (`record`) checks them against real screens.
 */
internal object PromptMarkers {
    /** Question of a permission dialog. */
    val PERMISSION = Regex("Do you want to ")

    /** Title row of a permission dialog, e.g. `Bash command`; group 1 is the tool. */
    val TOOL_TITLE = Regex("^\\s*(\\w+) (?:command|tool use)\\s*$")

    /** Footer of a selection list (the question tool). */
    val SELECT_FOOTER = Regex("Enter to select")

    /** Numbered choice row, optionally with the pointer; group 1 is the number. */
    val CHOICE = Regex("^\\s*(?:❯\\s*)?(\\d+)\\.\\s")

    /** Input box row: a border, then the prompt sign. */
    val INPUT_BOX = Regex("^\\s*[│|]\\s*>")

    /** Input box row that holds text: something other than blanks and the border follows the prompt sign. */
    val INPUT_FILLED = Regex("^\\s*[│|]\\s*>\\s+[^\\s│|]")

    /** Horizontal rule of the real TUI; the input box sits right under one. */
    val RULE = Regex("^\\s*[─╌]{10,}")

    /** Input row of the real TUI: the pointer, then the typed text (maybe none). */
    val POINTER_ROW = Regex("^\\s*❯(\\s+(\\S.*))?\\s*$")

    /** Rows the question tool adds to the real options: not answers to count. */
    val EXTRA_CHOICE = Regex("Type something|Chat about this")

    /** Spinner row: a glyph, a word and an ellipsis, e.g. `✻ Working…` or `✻ Thinking… (3s)`. */
    val SPINNER = Regex("^\\s*[✻✽✶✳✢·*]\\s+\\S+…")
}

/** Pure: the [Prompt] shown by the rows of a screen. */
internal object ScreenClassifier {
    fun classify(rows: List<String>): Prompt {
        val choices =
            rows.mapNotNull {
                PromptMarkers.CHOICE
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        val input = inputBox(rows)
        return when {
            choices.isNotEmpty() && rows.any(PromptMarkers.PERMISSION::containsMatchIn) -> Prompt.Permission(tool(rows))

            choices.isNotEmpty() &&
                rows.any(
                    PromptMarkers.SELECT_FOOTER::containsMatchIn,
                )
            -> Prompt.Options(optionCount(rows))

            rows.any(PromptMarkers.SPINNER::containsMatchIn) -> Prompt.Busy

            input == Prompt.TextFilled -> Prompt.TextFilled

            input == Prompt.Text -> Prompt.Text

            choices.size > 1 -> Prompt.Unknown

            else -> Prompt.None
        }
    }

    // Options the user can pick; "Type something." and "Chat about this" are rows of the TUI itself.
    private fun optionCount(rows: List<String>): Int =
        rows.count { PromptMarkers.CHOICE.containsMatchIn(it) && !PromptMarkers.EXTRA_CHOICE.containsMatchIn(it) }

    private fun inputBox(rows: List<String>): Prompt? =
        realInput(rows)
            ?: when {
                rows.any(PromptMarkers.INPUT_FILLED::containsMatchIn) -> Prompt.TextFilled
                rows.any(PromptMarkers.INPUT_BOX::containsMatchIn) -> Prompt.Text
                else -> null
            }

    // Real input box: the pointer row right under a rule (an echoed user message is not under one).
    private fun realInput(rows: List<String>): Prompt? =
        rows
            .zipWithNext()
            .firstNotNullOfOrNull { (above, row) ->
                if (PromptMarkers.RULE.containsMatchIn(above)) PromptMarkers.POINTER_ROW.find(row) else null
            }?.let { if (it.groupValues[2].isEmpty()) Prompt.Text else Prompt.TextFilled }

    private fun tool(rows: List<String>): String? =
        rows.firstNotNullOfOrNull {
            PromptMarkers.TOOL_TITLE
                .find(it)
                ?.groupValues
                ?.get(1)
        }
}
