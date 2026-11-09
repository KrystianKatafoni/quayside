package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText

/**
 * The consent step "Zaufać katalogowi?" of the "Nowy agent" dialog (FR-S3, ADR 0031): the directory on its own line,
 * what trust allows, and two buttons. The focus starts on "Anuluj" and never on the button that grants trust (NFR-6),
 * so Space or Enter pressed right after the step opens decline.
 */
@Composable
internal fun TrustStepView(
    step: TrustStep,
    onEvent: (NewAgentEvent) -> Unit,
) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestFocus() }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MissionControlTags.NEW_AGENT_TRUST),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AmText(
            MissionControlStrings.format(UiText("newAgent.trust.path", listOf(step.dir.toString()))),
            Modifier.fillMaxWidth().testTag(MissionControlTags.NEW_AGENT_TRUST_PATH),
            maxLines = Int.MAX_VALUE,
        )
        Wrapped(MissionControlStrings["newAgent.trust.warning"], Tone.WARN, MissionControlTags.NEW_AGENT_TRUST_WARNING)
        TrustButtons(step, cancel, onEvent)
    }
}

@Composable
private fun TrustButtons(
    step: TrustStep,
    cancel: FocusRequester,
    onEvent: (NewAgentEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
    ) {
        DialogButton(
            MissionControlStrings["newAgent.trust.cancel"],
            !step.saving,
            Modifier.focusRequester(cancel).testTag(MissionControlTags.NEW_AGENT_TRUST_CANCEL),
        ) {
            onEvent(NewAgentEvent.TrustDecline)
        }
        DialogButton(
            MissionControlStrings["newAgent.trust.accept"],
            !step.saving,
            Modifier.testTag(MissionControlTags.NEW_AGENT_TRUST_ACCEPT),
        ) {
            onEvent(NewAgentEvent.TrustAccept)
        }
    }
}
