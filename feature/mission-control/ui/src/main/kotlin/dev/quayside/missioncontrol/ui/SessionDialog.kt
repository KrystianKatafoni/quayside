package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim

/**
 * The open [dialog] over the whole window, inside it rather than a separate window (like `ProjectsDialog`).
 * Escape and a click outside are "Cancel".
 */
@Composable
internal fun SessionDialogView(
    dialog: SessionDialog,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = { onEvent(MissionControlEvent.Dismiss) },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().amScrim(),
            contentAlignment = Alignment.Center,
        ) {
            when (dialog) {
                is SessionDialog.ConfirmRemove -> ConfirmRemove(dialog, onEvent)
                is SessionDialog.AssignPlace -> PlaceDialog(dialog, onEvent)
            }
        }
    }
}

@Composable
private fun ConfirmRemove(
    dialog: SessionDialog.ConfirmRemove,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AmPanel(
        modifier =
            Modifier
                .width(width = 440.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { escape(it, onEvent) }
                .testTag(MissionControlTags.CONFIRM_REMOVE)
                .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            AmText(MissionControlStrings["confirmRemove.title"], role = TextRole.HEADING)
            // The name is user data: shown here, never logged (ENG-C8).
            AmText(dialog.name, role = TextRole.CODE, maxLines = Int.MAX_VALUE)
            val hint = if (dialog.fromDisk) "confirmRemove.hint.disk" else "confirmRemove.hint"
            AmText(MissionControlStrings[hint], color = mutedColor(), maxLines = Int.MAX_VALUE)
            Button("confirmRemove.confirm", MissionControlTags.CONFIRM_REMOVE_OK) {
                onEvent(MissionControlEvent.ConfirmRemove)
            }
            Button("dialog.cancel", MissionControlTags.CONFIRM_REMOVE_CANCEL) { onEvent(MissionControlEvent.Dismiss) }
        }
    }
}

internal fun escape(
    event: KeyEvent,
    onEvent: (MissionControlEvent) -> Unit,
): Boolean {
    val pressed = event.type == KeyEventType.KeyDown && event.key == Key.Escape
    if (pressed) onEvent(MissionControlEvent.Dismiss)
    return pressed
}

@Composable
private fun Button(
    key: String,
    tag: String,
    onClick: () -> Unit,
) {
    AmButton(label = MissionControlStrings[key], onClick = onClick, modifier = Modifier.testTag(tag))
}

/** Places the dialog layer over the whole window, whatever the anchor. */
internal object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
