package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId

/** Semantics tags of the disk sessions (FR-P9) for UI tests. */
internal object HistoryTags {
    /** The last row of the list "jeszcze N starszych" and its link "Zmień limit". */
    const val OLDER_ROW = "missionControl.older"
    const val OLDER_CHANGE = "missionControl.older.change"

    /** The window "Stare sesje z dysku", its error text and "Zamknij". */
    const val DIALOG = "history.settings"
    const val ERROR = "history.settings.error"
    const val CLOSE = "history.settings.close"

    /** The "z dysku" badge in the row of the disk session [id]. */
    fun rowDisk(id: SessionId): String = "missionControl.rowDisk.${id.value}"

    /** The choice of the age limit [age] (an enum name) in the window. */
    fun age(age: String): String = "history.settings.age.$age"

    /** The choice of the count limit [limit] (an enum name) in the window. */
    fun limit(limit: String): String = "history.settings.limit.$limit"
}
