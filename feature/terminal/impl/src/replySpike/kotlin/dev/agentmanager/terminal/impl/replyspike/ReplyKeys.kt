package dev.agentmanager.terminal.impl.replyspike

/** What the user answers. [Text.value] is user data (ENG-C8): never logged. */
internal sealed interface Reply {
    data class Text(
        val value: String,
    ) : Reply

    /** 1-based, as numbered by the TUI. */
    data class Option(
        val index: Int,
    ) : Reply

    data class Permission(
        val allow: Boolean,
    ) : Reply
}

internal data class KeyVariant(
    val text: TextKeys = TextKeys.PASTE,
    val choice: ChoiceKeys = ChoiceKeys.DIGIT,
)

/** Bracketed paste + CR / plain characters + CR. */
internal enum class TextKeys { PASTE, TYPE }

/** "n" / down x (n-1) + CR. */
internal enum class ChoiceKeys { DIGIT, ARROWS }

private const val ESC = "\u001b"
private const val CR = "\r"
private const val PASTE_START = "$ESC[200~"
private const val PASTE_END = "$ESC[201~"
private const val ARROW_DOWN = "$ESC[B"

/** Pure: keystroke chunks for a reply. Strips C0 controls and ESC from text, so an answer cannot inject keys. */
internal object ReplyKeys {
    /** @throws IllegalArgumentException for an empty text (after stripping) or an option below 1. */
    fun keys(
        reply: Reply,
        variant: KeyVariant,
    ): List<String> =
        when (reply) {
            is Reply.Text -> text(reply.value, variant.text)
            is Reply.Option -> option(reply.index, variant.choice)
            is Reply.Permission -> listOf(if (reply.allow) "1" else ESC)
        }

    private fun text(
        value: String,
        variant: TextKeys,
    ): List<String> {
        val safe = value.filter { it == '\n' || !it.isISOControl() }
        require(safe.isNotEmpty()) { "answer text must not be empty" }
        return when (variant) {
            TextKeys.PASTE -> {
                listOf("$PASTE_START$safe$PASTE_END", CR)
            }

            TextKeys.TYPE -> {
                safe
                    .replace('\n', ' ')
                    .codePoints()
                    .toArray()
                    .map { String(Character.toChars(it)) } + CR
            }
        }
    }

    private fun option(
        index: Int,
        variant: ChoiceKeys,
    ): List<String> {
        require(index >= 1) { "option index is 1-based" }
        return when (variant) {
            ChoiceKeys.DIGIT -> listOf(index.toString())
            ChoiceKeys.ARROWS -> List(index - 1) { ARROW_DOWN } + CR
        }
    }
}
