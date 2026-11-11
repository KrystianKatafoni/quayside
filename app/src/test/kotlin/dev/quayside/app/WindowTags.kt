package dev.quayside.app

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind

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
    const val DETAILS_RESUME = "missionControl.details.resume"
    const val DETAILS_FAILURE = "missionControl.details.failure"
    const val DETAILS_NOTICE = "missionControl.details.notice"
    const val CONFIRM_REMOVE = "missionControl.confirmRemove"
    const val CONFIRM_REMOVE_OK = "missionControl.confirmRemove.ok"
    const val CONFIRM_REMOVE_CANCEL = "missionControl.confirmRemove.cancel"
    const val DETAILS_EMPTY = "missionControl.details.empty"
    const val PREVIEW = "missionControl.preview"
    const val PREVIEW_TEXT = "missionControl.preview.text"
    const val PREVIEW_CLOSE = "missionControl.preview.close"
    const val DETAILS_PREVIEW = "missionControl.details.preview"
    const val DETAILS_HISTORY = "missionControl.details.history"
    const val DETAILS_MESSAGES = "missionControl.details.messages"
    const val DETAILS_SUMMARY = "missionControl.details.summary"
    const val PREVIEW_LOAD_OLDER = "missionControl.preview.loadOlder"
    const val DETAILS_ATTACH = "missionControl.details.attach"
    const val DETAILS_REPLY = "missionControl.details.reply"
    const val ROW_MENU_ATTACH = "missionControl.rowMenu.attach"
    const val SIDEBAR = "projects.sidebar"
    const val ADD_BUTTON = "projects.add"
    const val MODE_BAR = "missionControl.modeBar"

    /** The top bar of the window (AM-816): `TopBarTags` of `app`, the bell of `mission-control:ui`. */
    const val TOP_BAR = "app.topBar"
    const val CRUMBS = "app.topBar.crumbs"
    const val SEARCH = "app.topBar.search"
    const val VOICE_CHIP = "app.topBar.voice"
    const val BELL = "missionControl.alertsBell"

    /** The hints under the list, the cheat sheet toggle and "Dodaj projekt z dysku…" (`internal` there). */
    const val KEY_HINTS = "missionControl.keyHints"
    const val SHORTCUTS_TOGGLE = "missionControl.shortcuts.toggle"
    const val SHORTCUTS = "missionControl.shortcuts"
    const val ADD_FROM_DISK = "projects.addFromDisk"
    const val HEADER = "missionControl.header"
    const val ROW_MENU_REMOVE = "projects.row.menu.remove"
    const val CONFIRM_REMOVE_PROJECT = "projects.dialog.remove"
    const val UNINSTALL_PROJECT_HOOKS = "projects.dialog.remove.hooks.uninstall"
    const val RENAME_DIALOG = "projects.dialog.rename"
    const val SUBFOLDERS_DIALOG = "projects.dialog.subfolders"
    const val RENAME_FIELD = "projects.dialog.rename.field"
    const val RENAME_SAVE = "projects.dialog.rename.save"

    const val TERMINAL_AREA = "terminal.area"

    /** Terminal mode (AM-809): the full screen button of `terminal:ui`, the session bar of `mission-control:ui` and the
     * rail of `projects:ui`; all `internal` there. */
    const val FULL_SCREEN = "terminal.fullScreen"
    const val MISSION_CONTROL = "missionControl"
    const val SESSION_BAR = "missionControl.sessionBar"
    const val SESSION_BAR_REPLY = "missionControl.sessionBar.reply"
    const val RAIL = "projects.rail"

    fun railButton(projectId: String): String = "projects.rail.$projectId"

    /** A group in the sidebar (AM-306, `projects:ui`, `internal` there): a subfolder row and the chevron. */
    fun subfolderRow(
        projectId: String,
        name: String,
    ): String = "projects.row.$projectId.sub.$name"

    fun chevron(projectId: String): String = "projects.row.$projectId.chevron"

    /** The group overview on the project page (AM-307, `internal` in `mission-control:ui`) and its subfolder row. */
    const val OVERVIEW = "missionControl.overview"

    fun overviewRow(name: String): String = "missionControl.overview.row.$name"

    /** The manual place (AM-314, `internal` in `mission-control:ui`): action, row menu item, list, mark. */
    const val DETAILS_ASSIGN_PLACE = "missionControl.details.assign_place"
    const val ROW_MENU_ASSIGN_PLACE = "missionControl.rowMenu.assign_place"
    const val PLACE_DIALOG = "missionControl.place.dialog"
    const val PLACE_CANCEL = "missionControl.place.cancel"
    const val DETAILS_PLACE = "missionControl.details.place"

    fun menuItem(id: String): String = "missionControl.rowMenu.$id"

    /** A line of the list of places by position (`auto`, `root`, `sub.0`, …), never by name. */
    fun placeChoice(id: String): String = "missionControl.place.choice.$id"

    /** The place badge of a session row (`internal`). */
    fun rowPlace(sessionId: String): String = "missionControl.rowPlace.$sessionId"

    /** The "Nowy agent" button of the page title (`missioncontrol:ui`, `internal` there) and the dialog (AM-608). */
    const val NEW_AGENT_BUTTON = "missionControl.newAgent.button"
    const val NEW_AGENT_DIALOG = "missionControl.newAgent.dialog"
    const val NEW_AGENT_PROJECT = "missionControl.newAgent.project"
    const val NEW_AGENT_SUBFOLDER = "missionControl.newAgent.subfolder"
    const val NEW_AGENT_PROMPT = "missionControl.newAgent.prompt"
    const val NEW_AGENT_NAME = "missionControl.newAgent.name"
    const val NEW_AGENT_AGENT = "missionControl.newAgent.agent"
    const val NEW_AGENT_MODEL = "missionControl.newAgent.model"
    const val NEW_AGENT_PERMISSION = "missionControl.newAgent.permission"
    const val NEW_AGENT_PROBLEM = "missionControl.newAgent.problem"
    const val NEW_AGENT_CANCEL = "missionControl.newAgent.cancel"
    const val NEW_AGENT_START = "missionControl.newAgent.start"
    const val NEW_AGENT_TRUST = "missionControl.newAgent.trust"
    const val NEW_AGENT_TRUST_PATH = "missionControl.newAgent.trust.path"
    const val NEW_AGENT_TRUST_CANCEL = "missionControl.newAgent.trust.cancel"
    const val NEW_AGENT_TRUST_ACCEPT = "missionControl.newAgent.trust.accept"

    /** The terminal settings dialog (AM-508) and its parts; tags of `terminal:ui`, `internal` there. */
    const val SETTINGS_DIALOG = "terminal.settings"
    const val SETTINGS_FONT_SIZE = "terminal.settings.fontSize"
    const val SETTINGS_SAVE = "terminal.settings.save"
    const val SETTINGS_CANCEL = "terminal.settings.cancel"

    /** The notification settings dialog (AM-704); tags of `notifications:ui`, `internal` there. */
    const val NOTIFICATION_SETTINGS_DIALOG = "notifications.settings"
    const val SUMMARY_SETTINGS_DIALOG = "summaries.settings"
    const val SUMMARY_USE_MODEL = "summaries.settings.useModel"
    const val SUMMARY_SETTINGS_SAVE = "summaries.settings.save"
    const val SUMMARY_RESTART_NOTE = "summaries.settings.restartNote"
    const val NOTIFICATION_SETTINGS_SAVE = "notifications.settings.save"
    const val NOTIFICATION_SETTINGS_CANCEL = "notifications.settings.cancel"
    const val NOTIFICATION_POPUP_POSITION = "notifications.settings.popup.position"
    const val NOTIFICATION_SPEECH_VOICE = "notifications.settings.speech.voice"
    const val NOTIFICATION_PREVIEW = "notifications.settings.speech.preview"
    const val NOTIFICATION_SPEECH_RATE = "notifications.settings.speech.rate"
    const val NOTIFICATION_QUIET_ENABLED = "notifications.settings.quiet.enabled"
    const val NOTIFICATION_QUIET_FROM = "notifications.settings.quiet.from"
    const val NOTIFICATION_QUIET_TO = "notifications.settings.quiet.to"

    fun notificationProjectProfile(id: String): String = "notifications.settings.projects.profile.$id"

    fun notificationCell(
        kind: NotificationKind,
        channel: NotificationChannel,
    ): String = "notifications.settings.matrix.${kind.name}.${channel.name}"

    /** The Appearance dialog (AM-803) and its parts; tags of `appearance:ui`, `internal` there. */
    const val APPEARANCE_DIALOG = "appearance.dialog"
    const val APPEARANCE_APPLY = "appearance.apply"
    const val APPEARANCE_CANCEL = "appearance.cancel"
    const val APPEARANCE_PREVIEW = "appearance.preview"

    fun appearanceTheme(schemeId: String): String = "appearance.theme.$schemeId"

    fun appearanceScale(name: String): String = "appearance.scale.$name"

    fun appearanceDensity(name: String): String = "appearance.density.$name"

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

    fun missionRowMenuButton(sessionId: String): String = "missionControl.rowMenuButton.$sessionId"

    // Disk sessions (FR-P9, AM-312): the names the window gives its nodes; the tests are the contract.

    /** The "z dysku" badge of a row. */
    fun rowDisk(sessionId: String): String = "missionControl.rowDisk.$sessionId"

    /** The last row of the list: "jeszcze N starszych" and its link "Zmień limit". */
    const val OLDER_ROW = "missionControl.older"
    const val OLDER_CHANGE = "missionControl.older.change"

    /** Palette command id and dialog of "Stare sesje z dysku…"; [age], [limit] are enum names. */
    const val HISTORY_COMMAND = "sessions.history"
    const val HISTORY_DIALOG = "history.settings"

    fun historyAge(age: String): String = "history.settings.age.$age"

    fun historyLimit(limit: String): String = "history.settings.limit.$limit"

    /** The question after adding a folder, its answers, and the age choice in the add dialog. */
    const val PAST_DIALOG = "projects.dialog.past"
    const val PAST_SHOW = "projects.dialog.past.show"
    const val PAST_LATER = "projects.dialog.past.later"

    fun addHistoryAge(age: String): String = "projects.dialog.add.historyAge.$age"
}
