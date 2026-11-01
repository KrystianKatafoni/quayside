package dev.quayside.notifications.api

import dev.quayside.core.domain.SessionId

/** Identity of one popup, unique while the app runs. Not logged. */
@JvmInline
public value class PopupId(
    public val value: String,
)

/**
 * One popup to draw (FR-N3). [project], [sessionName] and [detail] are user data (ENG-C8): `toString` shows them as
 * `***`.
 *
 * @property kind what happened; the UI picks the title and the colour from it.
 * @property session the session it is about; null for a popup that is not about a session (a terminal bell).
 * @property project display name of the session's project; null when unknown.
 * @property sessionName display name of the session.
 * @property detail the question, or the tool a permission is asked for; null when unknown or not applicable.
 * @property sticky stays until [PopupAction.Dismiss]; false: the center removes it after a few seconds.
 * @property collapsed older than [PopupSettings.maxVisible]: drawn as one compact line.
 */
public data class PopupModel(
    val id: PopupId,
    val kind: NotificationKind,
    val session: SessionId?,
    val project: String?,
    val sessionName: String?,
    val detail: String?,
    val sticky: Boolean,
    val collapsed: Boolean,
) {
    override fun toString(): String =
        "PopupModel(id=$id, kind=$kind, session=$session, project=${project?.let { HIDDEN }}, " +
            "sessionName=${sessionName?.let { HIDDEN }}, detail=${detail?.let { HIDDEN }}, sticky=$sticky, " +
            "collapsed=$collapsed)"
}

/** What the user can do with a popup now. Answering and Allow/Deny come with a later task (ADR 0014). */
public enum class PopupAction {
    /** Close the popup. */
    DISMISS,

    /** Open the session's terminal (attach) and close the popup. */
    ATTACH,

    /** Silence every notification of the session for 15 minutes and close its popups. */
    MUTE,
}

internal const val HIDDEN = "***"
