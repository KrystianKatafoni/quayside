package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeColors
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.domain.Outcome

/** The text fields of the scheme editor, in the order of the docs/08 table after key, name and radius. */
public enum class EditorField { KEY, NAME, RADIUS, BG, PANEL, TEXT, ACCENT, WARN, OK, ERR, WORK }

/** The eight colour fields in the column order of [SchemeColors]. */
internal val COLOR_FIELDS: List<EditorField> =
    listOf(
        EditorField.BG,
        EditorField.PANEL,
        EditorField.TEXT,
        EditorField.ACCENT,
        EditorField.WARN,
        EditorField.OK,
        EditorField.ERR,
        EditorField.WORK,
    )

private val KEY_PATTERN = Regex("[a-z0-9-]+")

private val START_COLORS =
    listOf("#111111", "#161616", "#EDEDED", "#60A5FA", "#F59E0B", "#4ADE80", "#F87171", "#22D3EE")

/**
 * What the user typed into the scheme editor (FR-A6): the text of every field and the [mode]. Immutable; the text is
 * never trusted, [toScheme] validates it.
 */
public data class SchemeEditor(
    val values: Map<EditorField, String>,
    val mode: ThemeMode,
) {
    /** The editor with [field] set to [value]. */
    public fun with(
        field: EditorField,
        value: String,
    ): SchemeEditor = copy(values = values + (field to value))

    /**
     * The scheme the fields describe, or null when one is invalid: key `[a-z0-9-]+`, name of 1 to 40 characters after
     * trimming, radius an integer in 0..24, every colour `#RRGGBB`. Never throws.
     */
    @Suppress("MagicNumber") // the eight colours are indexed in the column order of SchemeColors
    public fun toScheme(): CustomScheme? {
        val key = text(EditorField.KEY).takeIf { KEY_PATTERN.matches(it) }
        val name = text(EditorField.NAME).takeIf { it.isNotEmpty() && it.length <= CustomScheme.MAX_NAME }
        val radius = text(EditorField.RADIUS).toIntOrNull()?.takeIf { it in 0..CustomScheme.MAX_RADIUS }
        val colors = COLOR_FIELDS.map { Rgba.parse(text(it)) }
        if (key == null || name == null || radius == null || colors.any { it == null }) return null
        val c = colors.map { checkNotNull(it) }
        val palette = SchemeColors(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7])
        return CustomScheme(SchemeId(key), name, mode, palette, radius)
    }

    private fun text(field: EditorField): String = values[field].orEmpty().trim()

    public companion object {
        /** An editor with an empty key and name, radius 6 and the colours of the built-in graphite theme. */
        public fun blank(): SchemeEditor =
            SchemeEditor(
                values =
                    mapOf(EditorField.KEY to "", EditorField.NAME to "", EditorField.RADIUS to "6") +
                        COLOR_FIELDS.zip(START_COLORS),
                mode = ThemeMode.DARK,
            )
    }
}

/** What the editor shows under its fields. */
internal sealed interface EditorReport {
    /** A field is empty or invalid; nothing was derived. */
    data object Incomplete : EditorReport

    /** The scheme passes the NFR-6 audit and its id is free; [spec] is what would join the catalog. */
    data class Ready(
        val spec: ThemeSpec,
    ) : EditorReport

    /** [AppearanceApi.previewScheme] refused the scheme: a taken key or the violated contrast rules. */
    data class Refused(
        val error: SchemeError,
    ) : EditorReport
}

/** The report for [editor]: derived by [appearance] without saving anything. */
internal fun editorReport(
    editor: SchemeEditor,
    appearance: AppearanceApi,
): EditorReport {
    val scheme = editor.toScheme() ?: return EditorReport.Incomplete
    return when (val preview = appearance.previewScheme(scheme)) {
        is Outcome.Ok -> EditorReport.Ready(preview.value)
        is Outcome.Err -> EditorReport.Refused(preview.error)
    }
}
