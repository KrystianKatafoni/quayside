package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.sessions.api.RecentOutput
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionError

/** What the preview panel shows (AM-411). */
internal sealed interface PreviewContent {
    /** The first read is running. */
    data object Loading : PreviewContent

    /** The output tail; `toString` hides it (ENG-C8). */
    data class Text(
        val text: String,
    ) : PreviewContent {
        override fun toString(): String = "Text(text=***)"
    }

    /** The session printed nothing yet. */
    data object Empty : PreviewContent

    /** The read failed; [reason] is a fixed text. */
    data class Failed(
        val reason: UiText,
    ) : PreviewContent
}

/** The open preview of session [id]; [name] is shown only, never logged. */
internal data class PreviewUi(
    val id: SessionId,
    val name: String,
    val content: PreviewContent,
)

/** Pure rules of the preview (AM-411 plan, decision 3 and table 1). */
internal object SessionPreview {
    /** The id of the session whose output is read: the target of [selection] when [open], else null. */
    fun key(
        open: Boolean,
        sessions: List<Session>,
        selection: Selection,
    ): SessionId? = if (open) SessionActions.target(sessions, selection)?.id else null

    /** The content for the result of one read. */
    fun content(outcome: Outcome<RecentOutput, SessionError>): PreviewContent =
        when (outcome) {
            is Outcome.Ok -> {
                if (outcome.value.isEmpty) PreviewContent.Empty else PreviewContent.Text(outcome.value.text)
            }

            is Outcome.Err -> {
                PreviewContent.Failed(SessionActionMessages.reason(outcome.error))
            }
        }
}
