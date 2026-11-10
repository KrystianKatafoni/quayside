package dev.quayside.projects.ui

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

// The same two choices as the window "Stare sesje z dysku" of `mission-control:ui`; `ui` modules do not see each
// other, so the small row of options is a copy (AM-312 plan).

/** The options of one choice: their [options], the test tag and the text of each. */
private class ChoiceSpec<T>(
    val options: List<T>,
    val tagOf: (T) -> String,
    val labelOf: (T) -> String,
)

private val AGES =
    ChoiceSpec(
        HistoryAge.entries,
        { ProjectsTags.historyAge(it.name) },
        { ProjectsStrings["history.age.${it.name}"] },
    )

private val LIMITS =
    ChoiceSpec(
        HistoryLimit.entries,
        { ProjectsTags.historyLimit(it.name) },
        { ProjectsStrings["history.limit.${it.name}"] },
    )

/** The two global choices of the disk sessions in the add dialog; a click saves at once (FR-P9). */
@Composable
internal fun HistoryChoices(
    choice: HistoryChoice,
    onEvent: (SidebarEvent) -> Unit,
) {
    DialogText(ProjectsStrings["history.hint"], muted = true)
    ChoiceRow(ProjectsStrings["history.age"], AGES, choice.maxAge) { onEvent(SidebarEvent.ChooseHistoryAge(it)) }
    ChoiceRow(ProjectsStrings["history.limit"], LIMITS, choice.maxPerProject) {
        onEvent(SidebarEvent.ChooseHistoryLimit(it))
    }
}

/** A titled row of options, one of them [selected] (radio buttons). */
@Composable
private fun <T> ChoiceRow(
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

/** One option: the chosen one has the selection fill and an accent frame, so it differs by more than the colour. */
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
