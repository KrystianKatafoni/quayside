package dev.agentmanager.app

/** Semantics tags of other modules that are `internal` there (the same trick as in `MissionControlEmptyTest`). */
internal object WindowTags {
    const val PALETTE = "palette"
    const val PALETTE_FIELD = "palette.field"

    /** The focused `Box` with `WindowKeys` that stands for `Window.onKeyEvent` in UI tests (see `showWindow`). */
    const val KEYS = "window.keys"
    const val DETAILS = "missionControl.details"
    const val DETAILS_STOP = "missionControl.details.stop"
    const val DETAILS_RESPAWN = "missionControl.details.respawn"
    const val DETAILS_REMOVE = "missionControl.details.remove"
    const val CONFIRM_REMOVE = "missionControl.confirmRemove"
    const val CONFIRM_REMOVE_OK = "missionControl.confirmRemove.ok"
    const val CONFIRM_REMOVE_CANCEL = "missionControl.confirmRemove.cancel"
    const val DETAILS_EMPTY = "missionControl.details.empty"
    const val PREVIEW = "missionControl.preview"
    const val PREVIEW_TEXT = "missionControl.preview.text"
    const val PREVIEW_CLOSE = "missionControl.preview.close"
    const val DETAILS_PREVIEW = "missionControl.details.preview"
    const val SIDEBAR = "projects.sidebar"
    const val ADD_BUTTON = "projects.add"
    const val MODE_BAR = "missionControl.modeBar"
    const val HEADER = "missionControl.header"
    const val RENAME_DIALOG = "projects.dialog.rename"
    const val RENAME_FIELD = "projects.dialog.rename.field"
    const val RENAME_SAVE = "projects.dialog.rename.save"

    const val TERMINAL_AREA = "terminal.area"

    /** Tags of `terminal:ui`, which are `internal` there; tab and pane ids count from 1 in opening order. */
    fun terminalGroup(projectId: String): String = "terminal.group.$projectId"

    fun terminalTab(n: Int): String = "terminal.tab.$n"

    fun terminalTabClose(n: Int): String = "terminal.tab.$n.close"

    fun terminalTabExit(n: Int): String = "terminal.tab.$n.exit"

    /** The state dot of a keyed tab (AM-510); its `stateDescription` is the text of the status. */
    fun terminalTabStatus(n: Int): String = "terminal.tab.$n.status"

    fun terminalGroupAdd(projectId: String): String = "terminal.group.$projectId.add"

    fun terminalPane(n: Int): String = "terminal.pane.$n"

    /** The marker that stands for the widget of a fake session (see `RecordingTerminals.paneContent`). */
    fun terminalContent(sessionId: String): String = "terminal.content.$sessionId"

    fun paletteRow(commandId: String): String = "palette.row.$commandId"

    fun projectRow(projectId: String): String = "projects.row.$projectId"

    /** The entry of a global view in the left column; [name] is `waiting`, `working` or `failed`. */
    fun view(name: String): String = "missionControl.view.$name"

    fun missionRow(sessionId: String): String = "missionControl.row.$sessionId"
}
