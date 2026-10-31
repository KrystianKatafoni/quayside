package dev.agentmanager.terminal.impl.replyspike

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
        return when {
            choices.isNotEmpty() && rows.any(PromptMarkers.PERMISSION::containsMatchIn) -> Prompt.Permission(tool(rows))

            choices.isNotEmpty() &&
                rows.any(
                    PromptMarkers.SELECT_FOOTER::containsMatchIn,
                )
            -> Prompt.Options(choices.max())

            rows.any(PromptMarkers.SPINNER::containsMatchIn) -> Prompt.Busy

            rows.any(PromptMarkers.INPUT_FILLED::containsMatchIn) -> Prompt.TextFilled

            rows.any(PromptMarkers.INPUT_BOX::containsMatchIn) -> Prompt.Text

            choices.size > 1 -> Prompt.Unknown

            else -> Prompt.None
        }
    }

    private fun tool(rows: List<String>): String? =
        rows.firstNotNullOfOrNull {
            PromptMarkers.TOOL_TITLE
                .find(it)
                ?.groupValues
                ?.get(1)
        }
}
