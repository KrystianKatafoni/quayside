package dev.quayside.appearance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmListRow
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.terminal.api.TerminalSettingsRules

// The size controls of the dialog: the interface scale, the list density, the terminal font and its size. Every
// choice is a focusable row of one selectable group; nothing here is dangerous to reach by a key (docs/08).

/** S, M and L as one row of choices. */
@Composable
internal fun ScaleChoice(
    draft: AppearanceDraft,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Segments(
        UiScale.entries,
        draft.settings.uiScale,
        { AppearanceStrings["scale.${it.name}"] },
        { onIntent(AppearanceIntent.SelectScale(it)) },
        { Modifier.testTag(AppearanceTags.scale(it)) },
    )
}

/** Compact and comfortable. */
@Composable
internal fun DensityChoice(
    draft: AppearanceDraft,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Segments(
        ListDensity.entries,
        draft.settings.density,
        { AppearanceStrings["density.${it.name}"] },
        { onIntent(AppearanceIntent.SelectDensity(it)) },
    ) { Modifier.testTag(AppearanceTags.density(it)) }
}

/** The row of [options]; [modifierOf] gives the tag (and more) of one choice. */
@Composable
private fun <T> Segments(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifierOf: (T) -> Modifier,
) {
    Row(Modifier.selectableGroup(), Arrangement.spacedBy(space = 6.dp)) {
        options.forEach { option ->
            AmListRow(option == selected, { onSelect(option) }, modifierOf(option)) { AmText(label(option)) }
        }
    }
}

/**
 * The terminal fonts of the draft's choices as one selectable group: "Z typografii" first, then the fonts of the
 * catalog; a saved font outside the catalog is listed last so that the current choice is always visible. The first
 * row carries [entry] (the control after the preview).
 */
@Composable
internal fun TerminalFontList(
    draft: AppearanceDraft,
    choices: List<String?>,
    onIntent: (AppearanceIntent) -> Unit,
    entry: FocusRequester,
) {
    val rows = if (draft.terminalFont in choices) choices else choices + draft.terminalFont
    LazyColumn(Modifier.testTag(AppearanceTags.TERMINAL_FONTS).heightIn(max = 150.dp).selectableGroup()) {
        items(rows, key = { it.orEmpty() }) { family ->
            val first = if (family == rows.first()) Modifier.focusRequester(entry) else Modifier
            AmListRow(
                selected = family == draft.terminalFont,
                onClick = { onIntent(AppearanceIntent.SelectTerminalFont(family)) },
                modifier = Modifier.fillMaxWidth().testTag(AppearanceTags.terminalFont(family)).then(first),
            ) {
                AmText(family ?: AppearanceStrings["terminalFont.fromTypography"])
            }
        }
    }
}

/** "−", the size and "+"; each button is disabled at its end of the range (no wrap). */
@Composable
internal fun TerminalSizeStepper(
    size: Int,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AmButton(
            "−",
            { onIntent(AppearanceIntent.StepTerminalSize(-1)) },
            Modifier.testTag(AppearanceTags.TERMINAL_SIZE_MINUS).described("terminalSize.minus"),
            ButtonOptions(enabled = size > TerminalSettingsRules.FONT_SIZES.first),
        )
        AmText(
            size.toString(),
            Modifier.testTag(AppearanceTags.TERMINAL_SIZE_VALUE).semantics {
                contentDescription = AppearanceStrings.format("terminalSize.value", size)
            },
        )
        AmButton(
            "+",
            { onIntent(AppearanceIntent.StepTerminalSize(1)) },
            Modifier.testTag(AppearanceTags.TERMINAL_SIZE_PLUS).described("terminalSize.plus"),
            ButtonOptions(enabled = size < TerminalSettingsRules.FONT_SIZES.last),
        )
    }
}

/** Screen readers get the text of [key] instead of the "−" and "+" of the button. */
private fun Modifier.described(key: String): Modifier = semantics { contentDescription = AppearanceStrings[key] }
