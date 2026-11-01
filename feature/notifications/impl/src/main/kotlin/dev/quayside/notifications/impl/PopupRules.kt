package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupModel
import dev.quayside.sessions.api.SessionState

/** Pure rules of the popup stack (FR-N3); no clock and no state. */
internal object PopupRules {
    /** Collapsed lines kept below the full popups; older ones are dropped. */
    const val MAX_COLLAPSED = 5

    /** Whether a popup of [kind] waits for the user under [duration] (docs/07). */
    fun sticky(
        kind: NotificationKind,
        duration: PopupDuration,
    ): Boolean =
        when (duration) {
            PopupDuration.ALWAYS_STAY -> true
            PopupDuration.ALWAYS_TIMED -> false
            PopupDuration.QUESTIONS_STAY -> kind == NotificationKind.NEEDS_INPUT || kind == NotificationKind.PERMISSION
        }

    /** The text a popup shows besides the names: the question or the tool; null for other states. */
    fun detailOf(state: SessionState?): String? =
        when (state) {
            is SessionState.AwaitingInput -> state.question
            is SessionState.AwaitingPermission -> state.tool
            else -> null
        }

    /**
     * [newest] first, then [older] without the popup it replaces (same session and kind, session not null).
     * The first [maxVisible] stay full, the next [MAX_COLLAPSED] are marked collapsed, the rest is dropped.
     */
    fun stack(
        newest: PopupModel,
        older: List<PopupModel>,
        maxVisible: Int,
    ): List<PopupModel> = arrange(listOf(newest) + older.filterNot { replaces(newest, it) }, maxVisible)

    /** Recomputes [PopupModel.collapsed] and the cap of [popups] (newest first) after a change. */
    fun arrange(
        popups: List<PopupModel>,
        maxVisible: Int,
    ): List<PopupModel> =
        popups
            .take(maxVisible + MAX_COLLAPSED)
            .mapIndexed { index, popup -> popup.copy(collapsed = index >= maxVisible) }

    private fun replaces(
        newest: PopupModel,
        older: PopupModel,
    ): Boolean = newest.session != null && newest.session == older.session && newest.kind == older.kind
}
