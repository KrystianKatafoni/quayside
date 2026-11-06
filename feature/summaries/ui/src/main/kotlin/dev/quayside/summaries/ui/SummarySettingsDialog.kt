package dev.quayside.summaries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.core.designsystem.amScrim
import dev.quayside.core.designsystem.toColor

/**
 * The "Ustawienia podsumowań" dialog over the whole window (FR-S7); draws nothing while [controller] has it closed.
 * A focusable popup covering the whole window, so keys reach its controls and not the window behind it; Escape is
 * "Cancel". The checkbox takes the focus when the dialog opens: it only changes the draft, nothing is written
 * before "Zapisz".
 */
@Composable
public fun SummarySettingsDialog(controller: SummarySettingsController) {
    val state by controller.state.collectAsState()
    val shown = state ?: return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = controller::cancel,
        properties = PopupProperties(focusable = true),
    ) {
        Box(modifier = Modifier.fillMaxSize().amScrim(), contentAlignment = Alignment.Center) {
            SummaryPanel(shown, controller)
        }
    }
}

@Composable
private fun SummaryPanel(
    state: SummaryDialogState,
    controller: SummarySettingsController,
) {
    AmPanel(modifier = Modifier.panel(controller::cancel)) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
            AmText(SummaryStrings["title"], role = TextRole.HEADING)
            AmText(SummaryStrings["info"], color = mutedColor())
            UseModelToggle(state.useModel, controller::setUseModel)
            AmText(SummaryStrings["costNote"], role = TextRole.CAPTION, color = mutedColor())
            if (state.restartNeeded) AmText(SummaryStrings["restartNote"], Modifier.testTag(SummaryTags.RESTART_NOTE))
            state.saveError?.let {
                AmText(SummaryStrings.of(it), Modifier.testTag(SummaryTags.SAVE_ERROR), color = errColor())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                AmButton(SummaryStrings["save"], controller::save, Modifier.testTag(SummaryTags.SAVE))
                AmButton(SummaryStrings["cancel"], controller::cancel, Modifier.testTag(SummaryTags.CANCEL))
            }
        }
    }
}

/** The outer frame of the panel: 520 dp wide, Escape calls [onEscape]. */
private fun Modifier.panel(onEscape: () -> Unit): Modifier =
    padding(all = 24.dp)
        .width(width = 520.dp)
        .testTag(SummaryTags.DIALOG)
        .onPreviewKeyEvent { event ->
            val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
            if (escape) onEscape()
            escape
        }

/** The checkbox of the switch; Space toggles it and it has the focus when the dialog opens. */
@Composable
private fun UseModelToggle(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Row(
        modifier =
            Modifier
                .focusRequester(first)
                .testTag(SummaryTags.USE_MODEL)
                .amFocusFrame()
                .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(on)
        AmText(SummaryStrings["useModel"])
    }
}

/** The box of a checkbox: a tick when [on]; the label next to it carries the semantics. */
@Composable
private fun CheckMark(on: Boolean) {
    val colors = AmTheme.colors
    val (fill, tick) =
        if (on) colors.content.accent to colors.content.accentInk else colors.surfaces.raised to colors.content.text
    Box(
        Modifier
            .width(width = 18.dp)
            .background(color = fill.toColor(), shape = AmTheme.shape.corner)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AmText(text = if (on) "✓" else " ", color = tick.toColor())
    }
}

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
private fun mutedColor() =
    AmTheme.colors.content.muted
        .toColor()

@Composable
private fun errColor() =
    AmTheme.colors.status.err
        .toColor()
