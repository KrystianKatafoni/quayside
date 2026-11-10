package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim
import dev.quayside.core.designsystem.toColor

/**
 * The window "Stare sesje z dysku" over the whole window (FR-P9); draws nothing while [controller] has it closed.
 * A focusable popup covering the window, so keys reach its controls and not the window behind it; Escape closes it.
 * A choice is saved at once. The start focus is on "Zamknij", a button that changes nothing (NFR-5).
 */
@Composable
public fun HistorySettingsDialog(controller: HistorySettingsController) {
    val state by controller.state.collectAsState()
    val shown = state ?: return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = controller::close,
        properties = PopupProperties(focusable = true),
    ) {
        Box(modifier = Modifier.fillMaxSize().amScrim(), contentAlignment = Alignment.Center) {
            HistoryPanel(shown, controller)
        }
    }
}

@Composable
private fun HistoryPanel(
    state: HistoryDialogState,
    controller: HistorySettingsController,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AmPanel(modifier = Modifier.panel(controller)) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
            AmText(MissionControlStrings["history.title"], role = TextRole.HEADING)
            AmText(MissionControlStrings["history.info"], color = mutedColor(), maxLines = Int.MAX_VALUE)
            HistoryBody(state, controller)
            AmButton(
                MissionControlStrings["history.close"],
                controller::close,
                Modifier.focusRequester(focus).testTag(HistoryTags.CLOSE),
            )
        }
    }
}

/** The two choices and, after a failed save, the fixed error text. */
@Composable
private fun HistoryBody(
    state: HistoryDialogState,
    controller: HistorySettingsController,
) {
    ChoiceRow(MissionControlStrings["history.age"], HistoryChoices.ages, state.maxAge, controller::chooseAge)
    ChoiceRow(
        MissionControlStrings["history.limit"],
        HistoryChoices.limits,
        state.maxPerProject,
        controller::chooseLimit,
    )
    state.saveError?.let {
        AmText(
            MissionControlStrings["history.error.${it.name}"],
            Modifier.testTag(HistoryTags.ERROR),
            color =
                AmTheme.colors.status.err
                    .toColor(),
            maxLines = Int.MAX_VALUE,
        )
    }
}

/** The outer frame of the panel: 520 dp wide; Escape closes the dialog while the focus is inside it. */
private fun Modifier.panel(controller: HistorySettingsController): Modifier =
    padding(all = 24.dp).width(width = 520.dp).testTag(HistoryTags.DIALOG).onPreviewKeyEvent { event ->
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape) controller.close()
        escape
    }
