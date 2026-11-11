package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions

/**
 * The "in progress" text or the failure, then the buttons of [detailsControls]: the main one, the ordinary ones and
 * the dangerous ones apart (C).
 */
@Composable
internal fun SessionActionsView(
    details: SessionDetails,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        ActionStatus(details)
        val controls = details.detailsControls()
        val firstDanger = controls.firstOrNull { it.kind == ButtonKind.DANGER }
        controls.forEach { control ->
            if (control === firstDanger) Spacer(Modifier.height(8.dp))
            ControlButton(control, onEvent, Modifier.fillMaxWidth())
        }
    }
}

/** The "in progress" text, the failure and the notice of a resumed copy; each only when it applies. */
@Composable
private fun ActionStatus(details: SessionDetails) {
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
    details.notice?.let {
        Box(Modifier.testTag(MissionControlTags.DETAILS_NOTICE)) {
            AmText(MissionControlStrings.format(it), color = mutedColor())
        }
    }
}

/** A button of the panel; it never takes focus (Space and Enter belong to the window). */
@Composable
internal fun ControlButton(
    control: SessionControl,
    onEvent: (MissionControlEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        ButtonOptions(
            kind = control.kind,
            focusable = false,
            icon = control.icon,
            shortcut = control.shortcutIn(LocalKeyCaps.current),
        )
    AmButton(
        MissionControlStrings[control.labelKey],
        { onEvent(control.event) },
        modifier.testTag(MissionControlTags.details(control.id)),
        options,
    )
}

private fun SessionControl.shortcutIn(caps: SessionKeyCaps?): String? =
    when (id) {
        "attach" -> caps?.attach
        "reply" -> caps?.reply
        "stop" -> caps?.stop
        else -> null
    }
