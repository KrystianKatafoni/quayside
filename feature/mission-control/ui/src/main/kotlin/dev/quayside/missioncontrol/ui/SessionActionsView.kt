package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme

/** Attach and reply buttons, then stop, respawn, remove and "Podgląd", then the "in progress" text or the failure. */
@Composable
internal fun SessionActionsView(
    details: SessionDetails,
    onEvent: (MissionControlEvent) -> Unit,
) {
    listOf(details.openControls(), details.otherControls()).filter { it.isNotEmpty() }.forEach { controls ->
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            controls.forEach { control ->
                ActionButton(MissionControlTags.details(control.id), control.labelKey) { onEvent(control.event) }
            }
        }
    }
    details.busy?.let {
        Box(Modifier.testTag(MissionControlTags.DETAILS_BUSY)) {
            AmText(MissionControlStrings["action.${it.name.lowercase()}.busy"], color = mutedColor())
        }
    }
    details.failure?.let {
        Column(Modifier.testTag(MissionControlTags.DETAILS_FAILURE)) {
            AmText(MissionControlStrings["action.${it.action.name.lowercase()}.failed"], color = Tone.ERR.tint())
            AmText(MissionControlStrings.format(it.reason), color = Tone.ERR.tint())
        }
    }
}

@Composable
private fun ActionButton(
    tag: String,
    labelKey: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(tag)
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.corner)
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = raisedColor())
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        AmText(MissionControlStrings[labelKey])
    }
}
