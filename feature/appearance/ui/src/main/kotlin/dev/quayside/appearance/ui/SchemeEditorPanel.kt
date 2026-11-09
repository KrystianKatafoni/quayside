package dev.quayside.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmDropdown
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.core.designsystem.toColor

/**
 * The buttons "Nowy schemat…", "Usuń schemat" and "Eksportuj schemat…" under the theme list; remove and export act on
 * the custom scheme the list shows as chosen and are off for a built-in theme. All are off while [busy].
 */
@Composable
internal fun SchemeButtons(
    chosenCustom: Boolean,
    busy: Boolean,
    chosen: SchemeId,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AmButton(
            AppearanceStrings["schemes.new"],
            { onIntent(AppearanceIntent.OpenSchemeEditor) },
            Modifier.testTag(AppearanceTags.SCHEME_NEW),
            ButtonOptions(enabled = !busy),
        )
        AmButton(
            AppearanceStrings["schemes.remove"],
            { onIntent(AppearanceIntent.RemoveScheme(chosen)) },
            Modifier.testTag(AppearanceTags.SCHEME_REMOVE),
            ButtonOptions(enabled = !busy && chosenCustom),
        )
        AmButton(
            AppearanceStrings["schemes.export"],
            { onIntent(AppearanceIntent.ExportScheme(chosen)) },
            Modifier.testTag(AppearanceTags.SCHEME_EXPORT),
            ButtonOptions(enabled = !busy && chosenCustom),
        )
    }
}

/**
 * The scheme editor (FR-A6): key, name, corner radius, mode and the eight colours as `#RRGGBB` text, the contrast
 * report of [report] and a sample in the derived colours. "Dodaj schemat" is on only when the scheme passes the audit.
 */
@Composable
internal fun SchemeEditorPanel(
    editor: SchemeEditor,
    report: EditorReport,
    busy: Boolean,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag(AppearanceTags.EDITOR),
        Arrangement.spacedBy(space = 6.dp),
    ) {
        AmText(AppearanceStrings["editor.title"], role = TextRole.HEADING)
        listOf(
            listOf(EditorField.KEY, EditorField.NAME),
            listOf(EditorField.RADIUS),
        ).forEach { FieldRow(it, editor, onIntent) }
        ModeChoice(editor.mode, onIntent)
        COLOR_FIELDS.chunked(size = 2).forEach { FieldRow(it, editor, onIntent) }
        ReportLine(report)
        (report as? EditorReport.Ready)?.let { Sample(it.spec) }
        EditorButtons(report is EditorReport.Ready && !busy, busy, onIntent)
    }
}

@Composable
private fun FieldRow(
    fields: List<EditorField>,
    editor: SchemeEditor,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        fields.forEach { field ->
            Column(Modifier.weight(weight = 1f), Arrangement.spacedBy(space = 2.dp)) {
                AmText(
                    AppearanceStrings["editor.field.${field.name}"],
                    role = TextRole.CAPTION,
                    color =
                        AmTheme.colors.content.muted
                            .toColor(),
                )
                EditorTextField(editor.values[field].orEmpty(), AppearanceTags.editorField(field)) {
                    onIntent(AppearanceIntent.EditSchemeField(field, it))
                }
            }
        }
    }
}

/** A single-line text field in the colours of the applied theme; Tab leaves it. */
@Composable
private fun EditorTextField(
    value: String,
    tag: String,
    onChange: (String) -> Unit,
) {
    val text =
        AmTheme.colors.content.text
            .toColor()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .amFocusFrame()
                .background(
                    AmTheme.colors.surfaces.raised
                        .toColor(),
                    AmTheme.shape.corner,
                ).padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true,
        textStyle = AmTheme.typography.style(TextRole.BODY).copy(color = text),
        cursorBrush = SolidColor(text),
    )
}

@Composable
private fun ModeChoice(
    mode: ThemeMode,
    onIntent: (AppearanceIntent) -> Unit,
) {
    val modes = ThemeMode.entries
    AmDropdown(
        AmDropdownItems(modes.map { AppearanceStrings.of(it) }, modes.indexOf(mode)),
        AppearanceStrings["editor.mode"],
        { onIntent(AppearanceIntent.EditSchemeMode(modes[it])) },
        Modifier.fillMaxWidth().testTag(AppearanceTags.EDITOR_MODE),
    )
}

/** The fixed line under the fields; contrast problems are the rule labels of the audit, never user text. */
@Composable
private fun ReportLine(report: EditorReport) {
    val text =
        when (report) {
            EditorReport.Incomplete -> {
                AppearanceStrings["editor.incomplete"]
            }

            is EditorReport.Ready -> {
                AppearanceStrings["editor.ready"]
            }

            is EditorReport.Refused -> {
                refusalText(report.error)
            }
        }
    val bad = report !is EditorReport.Ready
    AmText(
        text,
        Modifier.testTag(AppearanceTags.EDITOR_REPORT),
        color = (if (bad) AmTheme.colors.status.err else AmTheme.colors.content.text).toColor(),
    )
}

private fun refusalText(error: SchemeError): String =
    when (error) {
        is SchemeError.LowContrast -> AppearanceStrings.format("editor.contrast", error.problems.joinToString("; "))
        is SchemeError.Taken -> AppearanceStrings["editor.taken"]
        else -> AppearanceStrings["editor.incomplete"]
    }

/** The derived background, panel, text and the five status colours, so the user sees the scheme before adding it. */
@Composable
private fun Sample(spec: ThemeSpec) {
    val colors = spec.colors
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(AppearanceTags.EDITOR_PREVIEW)
            .background(colors.surfaces.bg.toColor(), AmTheme.shape.corner)
            .padding(all = 8.dp),
        Arrangement.spacedBy(space = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth().background(colors.surfaces.panel.toColor()).padding(all = 6.dp)) {
            AmText(AppearanceStrings["editor.sample"], color = colors.content.text.toColor())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            listOf(
                colors.content.accent,
                colors.status.warn,
                colors.status.ok,
                colors.status.err,
                colors.status.work,
            ).forEach { Box(Modifier.size(size = 14.dp).background(it.toColor())) }
        }
    }
}

/** "Dodaj schemat" (primary, off until the scheme passes) and "Zamknij edytor". */
@Composable
private fun EditorButtons(
    canSave: Boolean,
    busy: Boolean,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AmButton(
            AppearanceStrings["editor.save"],
            { onIntent(AppearanceIntent.SaveScheme) },
            Modifier.testTag(AppearanceTags.EDITOR_SAVE),
            ButtonOptions(kind = ButtonKind.PRIMARY, enabled = canSave),
        )
        AmButton(
            AppearanceStrings["editor.cancel"],
            { onIntent(AppearanceIntent.CloseSchemeEditor) },
            Modifier.testTag(AppearanceTags.EDITOR_CANCEL),
            ButtonOptions(enabled = !busy),
        )
    }
}
