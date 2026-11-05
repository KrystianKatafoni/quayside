package dev.quayside.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designsystem.AmListRow
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.ColorTokens

private val THEME_LIST_MAX: Dp = 210.dp

/**
 * The themes of [catalog] as one selectable group; the row of the draft's theme takes the focus when the dialog opens
 * (never a button that saves, lesson of the consent dialog) and is the first one visible.
 */
@Composable
internal fun ThemeList(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
    onIntent: (AppearanceIntent) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val chosen = draft.settings.scheme
    val start = catalog.themes.indexOfFirst { it.id == chosen }.coerceAtLeast(minimumValue = 0)
    LaunchedEffect(Unit) { focus.requestFocus() }
    LazyColumn(
        modifier = Modifier.testTag(AppearanceTags.THEMES).heightIn(max = THEME_LIST_MAX).selectableGroup(),
        state = rememberLazyListState(initialFirstVisibleItemIndex = start),
    ) {
        items(catalog.themes, key = { it.id.value }) { theme ->
            val selected = theme.id == chosen
            ThemeRow(theme, selected, if (selected) Modifier.focusRequester(focus) else Modifier) {
                onIntent(AppearanceIntent.SelectScheme(theme.id))
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: ThemeSpec,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    AmListRow(
        selected,
        onClick,
        Modifier.fillMaxWidth().testTag(AppearanceTags.theme(theme.id)).then(modifier),
    ) {
        Swatch(theme.colors)
        AmText(theme.name, Modifier.weight(weight = 1f))
        AmText(AppearanceStrings.of(theme.mode), role = TextRole.CAPTION)
    }
}

/** The background, the accent and the text of a theme side by side, framed so that it shows on any row. */
@Composable
private fun Swatch(colors: ColorTokens) {
    val frame =
        AmTheme.colors.content.text
            .toColor()
    Row(Modifier.border(width = 1.dp, color = frame)) {
        listOf(colors.surfaces.bg, colors.content.accent, colors.content.text).forEach {
            Box(Modifier.size(size = 12.dp).background(it.toColor()))
        }
    }
}
