package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/** The panel of the selected session; [age] is the age text of its row, [conversation] the history of a disk one. */
@Composable
internal fun Details(
    details: SessionDetails?,
    age: UiText?,
    conversation: ConversationUi?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val panel =
        Modifier
            .width(width = 340.dp)
            .fillMaxHeight()
            .background(color = panelColor())
            .padding(all = 12.dp)
    if (details == null) {
        Box(modifier = panel.testTag(MissionControlTags.DETAILS_EMPTY), contentAlignment = Alignment.Center) {
            AmText(MissionControlStrings["details.none"], color = mutedColor())
        }
        return
    }
    Column(
        modifier = panel.testTag(MissionControlTags.DETAILS),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        AmText(details.name, role = TextRole.NAME)
        StateChip(details.glyph, age)
        shownSummary(details)?.let { Summary(it) }
        DetailsFields(details)
        // guard against a delayed stateIn: the history of the previous selection may be there for one frame
        if (details.fromDisk) ConversationExcerpt(conversation?.takeIf { it.id == details.id })
        Spacer(Modifier.weight(weight = 1f))
        if (details.fromDisk) HistoryButton(onEvent)
        SessionActionsView(details, onEvent)
    }
}

/** "Czeka na odpowiedź · 4 min": the state, and the age of the last activity when it is known. */
@Composable
private fun StateChip(
    glyph: Glyph,
    age: UiText?,
) {
    val state = MissionControlStrings[glyph.key]
    val label = if (age == null) state else "$state · ${MissionControlStrings.format(age)}"
    AmChip(
        label,
        Modifier.testTag(MissionControlTags.DETAILS_STATE).semantics(mergeDescendants = true) {},
        glyph.tone,
        glyph.icon,
    )
}

/** The summary; a disk session without one says so in a fixed text (AM-315). */
private fun shownSummary(details: SessionDetails): String? =
    details.summary ?: MissionControlStrings["conversation.empty"].takeIf { details.fromDisk }

@Composable
private fun Summary(summary: String) {
    Column(modifier = Modifier.testTag(MissionControlTags.DETAILS_SUMMARY)) {
        AmText(MissionControlStrings["details.summary"], role = TextRole.LABEL, color = mutedColor())
        AmText(summary, maxLines = SUMMARY_LINES)
    }
}

private const val SUMMARY_LINES = 4
