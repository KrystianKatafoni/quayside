package dev.quayside.terminal.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.terminal.api.TerminalSettingsField

/** Semantics tags of the terminal area for UI tests. A pane frame exposes `selected` = it is the active pane. */
internal object TerminalTags {
    const val AREA = "terminal.area"
    const val ERROR = "terminal.error"
    const val ERROR_DISMISS = "terminal.error.dismiss"

    /** The search bar of the active pane and its parts (AM-504). */
    const val SEARCH_BAR = "terminal.search"
    const val SEARCH_FIELD = "terminal.search.field"

    /** The `3/12` counter; absent while the text is empty, invalid or has no hits. */
    const val SEARCH_COUNTER = "terminal.search.counter"

    /** The fixed "no hits" text. */
    const val SEARCH_NO_HITS = "terminal.search.noHits"

    /** The fixed "invalid pattern" text. */
    const val SEARCH_INVALID = "terminal.search.invalid"
    const val SEARCH_CASE = "terminal.search.case"
    const val SEARCH_REGEX = "terminal.search.regex"
    const val SEARCH_UP = "terminal.search.up"
    const val SEARCH_DOWN = "terminal.search.down"
    const val SEARCH_CLOSE = "terminal.search.close"

    /** The settings dialog (AM-508), its fields in tab order, its buttons and messages. */
    const val SETTINGS_DIALOG = "terminal.settings"
    const val SETTINGS_SHELL = "terminal.settings.shell"
    const val SETTINGS_START_DIRECTORY = "terminal.settings.startDirectory"
    const val SETTINGS_FONT_FAMILY = "terminal.settings.fontFamily"
    const val SETTINGS_FONT_SIZE = "terminal.settings.fontSize"
    const val SETTINGS_SCROLLBACK = "terminal.settings.scrollback"
    const val SETTINGS_LIGATURES = "terminal.settings.ligatures"
    const val SETTINGS_OPTION_AS_META = "terminal.settings.optionAsMeta"
    const val SETTINGS_BELL = "terminal.settings.bellNotification"
    const val SETTINGS_SAVE = "terminal.settings.save"
    const val SETTINGS_CANCEL = "terminal.settings.cancel"

    /** The notice about `terminal.json` (recovered, unreadable, newer app). */
    const val SETTINGS_NOTICE = "terminal.settings.notice"

    /** The fixed "settings could not be saved" text. */
    const val SETTINGS_STORAGE_ERROR = "terminal.settings.storageError"

    /** The message under [field]; absent while the field is valid. */
    fun settingsInvalid(field: TerminalSettingsField): String = "terminal.settings.invalid.${field.name}"

    fun group(project: ProjectId): String = "terminal.group.${project.value}"

    /** The "+" at the end of a group: opens a shell in that project. */
    fun groupAdd(project: ProjectId): String = "terminal.group.${project.value}.add"

    fun tab(tab: TabId): String = "terminal.tab.${tab.value}"

    fun tabClose(tab: TabId): String = "terminal.tab.${tab.value}.close"

    /** The `exit N` marker of an exited tab. */
    fun tabExit(tab: TabId): String = "terminal.tab.${tab.value}.exit"

    /** The status dot of a keyed tab (AM-510); its `stateDescription` is the text of the status. */
    fun tabStatus(tab: TabId): String = "terminal.tab.${tab.value}.status"

    fun pane(pane: PaneId): String = "terminal.pane.${pane.value}"
}
