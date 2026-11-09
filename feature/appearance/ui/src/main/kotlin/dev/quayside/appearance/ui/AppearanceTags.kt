package dev.quayside.appearance.ui

import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId

/** Semantics tags of the Appearance dialog for UI tests (AM-803). */
internal object AppearanceTags {
    const val DIALOG = "appearance.dialog"

    /** The list of themes; one selectable group. */
    const val THEMES = "appearance.themes"

    /** The list of typographies; one selectable group. */
    const val TYPOGRAPHIES = "appearance.typographies"

    /** The "Motyw proponuje: X" row; absent while there is no suggestion. */
    const val SUGGESTION = "appearance.suggestion"
    const val SUGGESTION_USE = "appearance.suggestion.use"

    /** The two buttons of the terminal size: minus is disabled at the minimum, plus at the maximum. */
    const val TERMINAL_SIZE_MINUS = "appearance.terminalSize.minus"
    const val TERMINAL_SIZE_PLUS = "appearance.terminalSize.plus"
    const val TERMINAL_SIZE_VALUE = "appearance.terminalSize.value"

    /** The list of terminal fonts; one selectable group. */
    const val TERMINAL_FONTS = "appearance.terminalFonts"

    /** The panel around the preview slot; takes no click and no focus. */
    const val PREVIEW = "appearance.preview"

    /** The terminal sample under the preview: `termBg`, `termText`, the draft font and size. */
    const val TERMINAL_SAMPLE = "appearance.preview.terminal"
    const val APPLY = "appearance.apply"
    const val CANCEL = "appearance.cancel"

    /** The fixed message of an apply that failed; absent otherwise. */
    const val ERROR = "appearance.error"

    /** The switch "Dzień i noc z macOS". */
    const val DAY_NIGHT_SWITCH = "appearance.dayNight.switch"

    /** The segment "Dzień | Noc" above the theme list; absent while day/night is off. */
    const val SLOT_SEGMENT = "appearance.slot"

    /** The buttons "Importuj…" and "Eksportuj…". */
    const val IMPORT = "appearance.import"
    const val EXPORT = "appearance.export"

    /** The button "Importuj schemat…" (custom schemes of a theme file, FR-A6). */
    const val IMPORT_SCHEMES = "appearance.importSchemes"

    /** The fixed message after a finished import or export; absent otherwise. */
    const val NOTICE = "appearance.notice"

    /** The buttons under the theme list: new scheme, remove and export the chosen custom scheme. */
    const val SCHEME_NEW = "appearance.scheme.new"
    const val SCHEME_REMOVE = "appearance.scheme.remove"
    const val SCHEME_EXPORT = "appearance.scheme.export"

    /** The scheme editor and its parts; absent while the editor is closed. */
    const val EDITOR = "appearance.editor"
    const val EDITOR_MODE = "appearance.editor.mode"
    const val EDITOR_SAVE = "appearance.editor.save"
    const val EDITOR_CANCEL = "appearance.editor.cancel"

    /** The line under the fields: incomplete, taken key, low contrast or ready. */
    const val EDITOR_REPORT = "appearance.editor.report"

    /** The sample drawn with the derived colours; present only while the scheme passes the audit. */
    const val EDITOR_PREVIEW = "appearance.editor.preview"

    fun editorField(field: EditorField): String = "appearance.editor.field.${field.name}"

    fun slot(slot: ThemeSlot): String = "appearance.slot.${slot.name}"

    fun theme(id: SchemeId): String = "appearance.theme.${id.value}"

    fun typography(id: TypographyId): String = "appearance.typography.${id.value}"

    fun scale(scale: UiScale): String = "appearance.scale.${scale.name}"

    fun density(density: ListDensity): String = "appearance.density.${density.name}"

    /** The choice of the terminal font; null = "Z typografii". */
    fun terminalFont(family: String?): String = "appearance.terminalFont.${family ?: "fromTypography"}"
}
