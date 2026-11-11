package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmListRow
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/**
 * The list of places of one session (FR-P9, AM-314): the current line is selected and has the start focus, so Space or
 * Enter at the start picks what already holds and changes nothing. A click picks a line; Escape and "Anuluj" close.
 */
@Composable
internal fun PlaceDialog(
    dialog: SessionDialog.AssignPlace,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val focus = remember(dialog.id) { FocusRequester() }
    LaunchedEffect(dialog.id) { focus.requestFocus() }
    AmPanel(
        modifier =
            Modifier
                .width(width = 440.dp)
                .onPreviewKeyEvent { escape(it, onEvent) }
                .testTag(MissionControlTags.PLACE_DIALOG),
    ) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            AmText(MissionControlStrings["place.dialog.title"], role = TextRole.HEADING)
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                dialog.choices.forEach { choice -> PlaceLine(choice, dialog.current, focus, onEvent) }
            }
            AmButton(
                label = MissionControlStrings["dialog.cancel"],
                onClick = { onEvent(MissionControlEvent.Dismiss) },
                modifier = Modifier.testTag(MissionControlTags.PLACE_CANCEL),
            )
        }
    }
}

@Composable
private fun PlaceLine(
    choice: PlaceChoice,
    current: String,
    focus: FocusRequester,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val isCurrent = choice.id == current
    val tagged = Modifier.testTag(PlaceTags.choice(choice.id))
    AmListRow(
        selected = isCurrent,
        onClick = { onEvent(MissionControlEvent.PickPlace(choice.id)) },
        modifier = if (isCurrent) tagged.focusRequester(focus) else tagged,
    ) {
        AmText(MissionControlStrings.format(choice.label), maxLines = Int.MAX_VALUE)
    }
}
