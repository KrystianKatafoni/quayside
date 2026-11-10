package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.core.designsystem.toColor
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit

/** The options of one choice: their [options], the test tag and the text of each. */
internal class ChoiceSpec<T>(
    val options: List<T>,
    val tagOf: (T) -> String,
    val labelOf: (T) -> String,
)

/** The choices of the window "Stare sesje z dysku", in its order. */
internal object HistoryChoices {
    /** The age limits; the texts come from `history.age.*`. */
    val ages: ChoiceSpec<HistoryAge> =
        ChoiceSpec(
            HistoryAge.entries,
            { HistoryTags.age(it.name) },
            { MissionControlStrings["history.age.${it.name}"] },
        )

    /** The count limits; the texts come from `history.limit.*`. */
    val limits: ChoiceSpec<HistoryLimit> =
        ChoiceSpec(
            HistoryLimit.entries,
            { HistoryTags.limit(it.name) },
            { MissionControlStrings["history.limit.${it.name}"] },
        )
}

/** A titled row of options, one of them [selected]; a click on another one calls [onPick] (radio buttons). */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    spec: ChoiceSpec<T>,
    selected: T,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        AmText(title, color = mutedColor())
        Row(horizontalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            spec.options.forEach { option ->
                Choice(spec.labelOf(option), option == selected, Modifier.testTag(spec.tagOf(option))) {
                    onPick(option)
                }
            }
        }
    }
}

/**
 * One option: the chosen one has the selection fill and an accent frame, so it differs by more than the colour of the
 * text (the text stays `text` on `raised` or `sel`, as the audit counts it).
 */
@Composable
private fun Choice(
    label: String,
    chosen: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = AmTheme.colors
    val fill = if (chosen) colors.surfaces.sel else colors.surfaces.raised
    val frame = if (chosen) colors.content.accent else colors.surfaces.border
    Row(
        modifier =
            modifier
                .amFocusFrame()
                .background(fill.toColor(), AmTheme.shape.corner)
                .border(AmTheme.shape.borderWidth, frame.toColor(), AmTheme.shape.corner)
                .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AmText(label)
    }
}
