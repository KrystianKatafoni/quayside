package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim
import dev.quayside.sessions.api.SessionPermissionMode

/** The "Nowy agent ⌘N" dialog of [controller]; draws nothing while it is closed. */
@Composable
public fun NewAgentDialog(controller: NewAgentController) {
    val state by controller.state.collectAsState()
    state?.let { NewAgentPopup(it, controller::onEvent) }
}

/** A focusable `Popup` over the whole window: only the dialog gets keys while it is open (like `SessionDialogView`). */
@Composable
private fun NewAgentPopup(
    state: NewAgentState,
    onEvent: (NewAgentEvent) -> Unit,
) {
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = { onEvent(NewAgentEvent.Cancel) },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().amScrim(),
            contentAlignment = Alignment.Center,
        ) {
            Panel(state, onEvent)
        }
    }
}

/**
 * The focus starts on the prompt, never on a button (NFR-6), and returns there when a start ends or a problem
 * appears. Escape cancels and ⌘⏎ starts from every field; the controller ignores both while a start runs.
 * In the consent step the focus starts on "Anuluj", Escape declines and ⌘⏎ is ignored.
 */
@Composable
private fun Panel(
    state: NewAgentState,
    onEvent: (NewAgentEvent) -> Unit,
) {
    val prompt = remember { FocusRequester() }
    val asking = state.trust != null
    LaunchedEffect(state.busy, state.problem, asking) { if (!state.busy && !asking) prompt.requestFocus() }
    AmPanel(
        modifier =
            Modifier
                .width(width = 520.dp)
                .onPreviewKeyEvent { windowKey(it, asking, onEvent) }
                .testTag(MissionControlTags.NEW_AGENT_DIALOG),
    ) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            AmText(
                MissionControlStrings[if (asking) "newAgent.trust.title" else "newAgent.title"],
                role = TextRole.HEADING,
            )
            state.trust?.let { TrustStepView(it, onEvent) } ?: Form(state, prompt, onEvent)
        }
    }
}

@Composable
private fun Form(
    state: NewAgentState,
    prompt: FocusRequester,
    onEvent: (NewAgentEvent) -> Unit,
) {
    NewAgentFields(state, prompt, onEvent)
    Messages(state)
    Buttons(state.busy, onEvent)
}

private fun windowKey(
    event: KeyEvent,
    asking: Boolean,
    onEvent: (NewAgentEvent) -> Unit,
): Boolean {
    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
    val action =
        when {
            event.type != KeyEventType.KeyDown -> null
            event.key == Key.Escape -> if (asking) NewAgentEvent.TrustDecline else NewAgentEvent.Cancel
            enter && event.isMetaPressed -> NewAgentEvent.Submit
            else -> null
        }
    action?.let(onEvent)
    return action != null
}

/** The bypass warning, the running start and the problem; the warning and the problem are live regions. */
@Composable
private fun Messages(state: NewAgentState) {
    if (state.form.permission == SessionPermissionMode.BYPASS_PERMISSIONS) {
        Wrapped(MissionControlStrings["newAgent.bypassWarning"], Tone.WARN, MissionControlTags.NEW_AGENT_WARNING)
    }
    if (state.busy) AmText(MissionControlStrings["newAgent.starting"], color = mutedColor())
    state.problem?.let {
        Wrapped(MissionControlStrings.format(it.text()), Tone.ERR, MissionControlTags.NEW_AGENT_PROBLEM)
    }
}

@Composable
internal fun Wrapped(
    text: String,
    tone: Tone,
    tag: String,
) {
    AmText(
        text,
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        color = tone.tint(),
        maxLines = Int.MAX_VALUE,
    )
}

@Composable
private fun Buttons(
    busy: Boolean,
    onEvent: (NewAgentEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
    ) {
        DialogButton(
            MissionControlStrings["dialog.cancel"],
            !busy,
            Modifier.testTag(MissionControlTags.NEW_AGENT_CANCEL),
        ) {
            onEvent(NewAgentEvent.Cancel)
        }
        DialogButton(
            MissionControlStrings["newAgent.start"],
            !busy,
            Modifier.testTag(MissionControlTags.NEW_AGENT_START),
        ) {
            onEvent(NewAgentEvent.Submit)
        }
    }
}
