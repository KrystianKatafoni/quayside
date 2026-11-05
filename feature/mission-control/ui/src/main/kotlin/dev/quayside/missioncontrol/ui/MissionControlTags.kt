package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId

/** Semantics tags of Mission Control for UI tests. */
internal object MissionControlTags {
    const val ROOT = "missionControl"
    const val EMPTY = "missionControl.empty"
    const val HEADER = "missionControl.header"
    const val LOADING = "missionControl.loading"
    const val BANNER = "missionControl.banner"
    const val DETAILS = "missionControl.details"
    const val DETAILS_EMPTY = "missionControl.details.empty"
    const val DETAILS_STOP = "missionControl.details.stop"
    const val DETAILS_RESPAWN = "missionControl.details.respawn"
    const val DETAILS_REMOVE = "missionControl.details.remove"
    const val CONFIRM_REMOVE = "missionControl.confirmRemove"
    const val CONFIRM_REMOVE_OK = "missionControl.confirmRemove.ok"
    const val CONFIRM_REMOVE_CANCEL = "missionControl.confirmRemove.cancel"
    const val DETAILS_BUSY = "missionControl.details.busy"
    const val DETAILS_FAILURE = "missionControl.details.failure"
    const val PREVIEW = "missionControl.preview"
    const val PREVIEW_TEXT = "missionControl.preview.text"
    const val PREVIEW_CLOSE = "missionControl.preview.close"
    const val DETAILS_PREVIEW = "missionControl.details.preview"
    const val MODE_BAR = "missionControl.modeBar"
    const val DETAILS_ATTACH = "missionControl.details.attach"
    const val DETAILS_REPLY = "missionControl.details.reply"
    const val ROW_MENU = "missionControl.rowMenu"
    const val SHORTCUTS_TOGGLE = "missionControl.shortcuts.toggle"
    const val SHORTCUTS = "missionControl.shortcuts"
    const val NEW_AGENT = "missionControl.newAgent"
    const val NEW_AGENT_DIALOG = "missionControl.newAgent.dialog"
    const val NEW_AGENT_PROJECT = "missionControl.newAgent.project"
    const val NEW_AGENT_PROMPT = "missionControl.newAgent.prompt"
    const val NEW_AGENT_NAME = "missionControl.newAgent.name"
    const val NEW_AGENT_AGENT = "missionControl.newAgent.agent"
    const val NEW_AGENT_MODEL = "missionControl.newAgent.model"
    const val NEW_AGENT_PERMISSION = "missionControl.newAgent.permission"
    const val NEW_AGENT_WARNING = "missionControl.newAgent.warning"
    const val NEW_AGENT_PROBLEM = "missionControl.newAgent.problem"
    const val NEW_AGENT_CANCEL = "missionControl.newAgent.cancel"
    const val NEW_AGENT_START = "missionControl.newAgent.start"

    const val SESSION_BAR = "missionControl.sessionBar"
    const val SESSION_BAR_TILE = "missionControl.sessionBar.tile"
    const val SESSION_BAR_REPLY = "missionControl.sessionBar.reply"
    const val SESSION_BAR_RESPAWN = "missionControl.sessionBar.respawn"
    const val SESSION_BAR_STOP = "missionControl.sessionBar.stop"
    const val HEADER_PATH = "missionControl.header.path"
    const val HEADER_COUNT = "missionControl.header.count"
    const val DETAILS_STATE = "missionControl.details.state"
    const val DETAILS_SUMMARY = "missionControl.details.summary"

    /** The stat card of [kind]: `waiting`, `working`, `done` or `failed`. */
    fun stat(kind: String): String = "missionControl.stat.$kind"

    /** The "Odpowiedz" button in the selected row of session [id]. */
    fun rowReply(id: SessionId): String = "missionControl.rowReply.${id.value}"

    /** The Attach icon button in the selected row of session [id]. */
    fun rowAttach(id: SessionId): String = "missionControl.rowAttach.${id.value}"

    /** The button of the control [id] (`attach`, `reply`, `stop`, `respawn`, `remove`, `preview`) in the panel. */
    fun details(id: String): String = "missionControl.details.$id"

    /** The item of the control [id] in the open row menu. */
    fun menuItem(id: String): String = "missionControl.rowMenu.$id"

    /** The "⋯" button of the row of session [id]; clicking it opens the row menu. */
    fun rowMenuButton(id: SessionId): String = "missionControl.rowMenuButton.${id.value}"

    /** The filter button of [filter]. */
    fun filter(filter: SessionFilter): String = "missionControl.filter.${filter.name.lowercase()}"

    /** The entry of the global view [view] in `GlobalViews`; clicking it shows the view. */
    fun view(view: GlobalView): String = "missionControl.view.${view.name.lowercase()}"

    /** The row of session [id]; clicking it selects the session. */
    fun row(id: SessionId): String = "missionControl.row.${id.value}"
}
