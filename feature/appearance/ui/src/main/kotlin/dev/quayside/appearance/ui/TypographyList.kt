package dev.quayside.appearance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmListRow
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole

private val TYPOGRAPHY_LIST_MAX = 170.dp

/** The typographies of [catalog] as one selectable group, and the suggestion of the chosen theme under it. */
@Composable
internal fun TypographyList(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
    onIntent: (AppearanceIntent) -> Unit,
) {
    val chosen = draft.settings.typography
    val start = catalog.typographies.indexOfFirst { it.id == chosen }.coerceAtLeast(minimumValue = 0)
    Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        LazyColumn(
            modifier =
                Modifier
                    .testTag(
                        AppearanceTags.TYPOGRAPHIES,
                    ).heightIn(max = TYPOGRAPHY_LIST_MAX)
                    .selectableGroup(),
            state = rememberLazyListState(initialFirstVisibleItemIndex = start),
        ) {
            items(catalog.typographies, key = { it.id.value }) { spec ->
                AmListRow(
                    selected = spec.id == chosen,
                    onClick = { onIntent(AppearanceIntent.SelectTypography(spec.id)) },
                    modifier = Modifier.fillMaxWidth().testTag(AppearanceTags.typography(spec.id)),
                ) {
                    AmText(spec.name, Modifier.weight(weight = 1f))
                    AmText(spec.terminalFont, role = TextRole.CAPTION)
                }
            }
        }
        suggestedTypography(draft, catalog)?.let { id ->
            Suggestion(catalog.typography(id)?.name.orEmpty(), onIntent)
        }
    }
}

/** "Motyw proponuje: X" and the "Użyj" button; it changes nothing by itself (docs/08). */
@Composable
private fun Suggestion(
    name: String,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(AppearanceTags.SUGGESTION),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(AppearanceStrings.format("suggestion", name), Modifier.weight(weight = 1f))
        AmButton(
            AppearanceStrings["suggestion.use"],
            { onIntent(AppearanceIntent.UseSuggestedTypography) },
            Modifier.testTag(AppearanceTags.SUGGESTION_USE),
            ButtonOptions(kind = ButtonKind.SECONDARY),
        )
    }
}
