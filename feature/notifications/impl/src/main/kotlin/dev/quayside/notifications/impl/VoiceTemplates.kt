package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState

/** Fills the message templates of [dev.quayside.notifications.api.SpeechSettings] (FR-N4). */
internal object VoiceTemplates {
    private const val MAX_VALUE = 200
    private const val QUESTION = "{pytanie}"
    private val VARIABLE = Regex("""\{([^{}]+)}""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * The text to speak for [template], or null when nothing is left to say. [session] is null for a terminal bell,
     * [project] is the display name of its project. With [readQuestion] a [NotificationKind.NEEDS_INPUT] message
     * gets ". <question>" unless the template has `{pytanie}`.
     *
     * Values are user data: `[` and `]` are dropped (so they cannot embed `say` commands such as `[[volm 0]]`),
     * line breaks become spaces and each value is cut to 200 characters.
     */
    fun render(
        template: String,
        kind: NotificationKind,
        session: Session?,
        project: String?,
        readQuestion: Boolean = false,
    ): String? {
        val values = values(session, project)
        val base = collapse(VARIABLE.replace(template) { values[it.groupValues[1]] ?: it.value }).ifEmpty { null }
        val question = values.getValue("pytanie")
        val appended = readQuestion && kind == NotificationKind.NEEDS_INPUT && QUESTION !in template
        return if (appended && base != null && question.isNotBlank()) "$base. ${collapse(question)}" else base
    }

    private fun values(
        session: Session?,
        project: String?,
    ): Map<String, String> =
        mapOf(
            "sesja" to clean(session?.name?.replace('-', ' ')?.replace('_', ' ')),
            "projekt" to clean(project),
            "pytanie" to clean((session?.state as? SessionState.AwaitingInput)?.question),
            "narzędzie" to clean((session?.state as? SessionState.AwaitingPermission)?.tool),
            "podsumowanie" to clean(session?.summary),
        )

    private fun clean(value: String?): String =
        value
            .orEmpty()
            .filterNot { it == '[' || it == ']' }
            .let(::collapse)
            .take(MAX_VALUE)

    private fun collapse(text: String): String = text.replace(WHITESPACE, " ").trim()
}
