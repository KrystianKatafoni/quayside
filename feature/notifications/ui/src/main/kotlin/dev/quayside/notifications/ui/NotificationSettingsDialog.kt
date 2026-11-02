package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.notifications.api.NotificationSettingsError

private val panelShape = RoundedCornerShape(size = 10.dp)

/**
 * The "Ustawienia powiadomień" dialog over the whole window (FR-N1, FR-N2); draws nothing while [controller] has it
 * closed. A focusable popup covering the whole window, so keys reach its controls and not the window behind it;
 * the panel scrolls in the vertical axis; Escape is "Cancel".
 */
@Composable
public fun NotificationSettingsDialog(controller: NotificationSettingsController) {
    val state by controller.state.collectAsState()
    val shown = state ?: return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = controller::cancel,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(color = Color(color = 0x99000000)),
            contentAlignment = Alignment.Center,
        ) {
            SettingsPanel(shown, controller)
        }
    }
}

@Composable
private fun SettingsPanel(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    Column(
        modifier = Modifier.panel(controller::cancel),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Label(SettingsStrings["settings.title"])
        Label(SettingsStrings["settings.hint"], color = SettingsColors.secondary)
        GlobalSwitches(state.draft, controller)
        ChannelMatrix(state.draft, controller)
        ThresholdRow(state, controller)
        SettingsFooter(state.saveError, controller)
    }
}

/** The panel: 640 dp wide, scrolling in the vertical axis, Escape calls [onEscape]. */
@Composable
private fun Modifier.panel(onEscape: () -> Unit): Modifier =
    padding(all = 24.dp)
        .width(width = 640.dp)
        .background(color = SettingsColors.panel, shape = panelShape)
        .testTag(SettingsTags.DIALOG)
        .onPreviewKeyEvent { event ->
            val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
            if (escape) onEscape()
            escape
        }.verticalScroll(rememberScrollState())
        .padding(all = 16.dp)

@Composable
private fun ThresholdRow(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val invalid = state.invalid?.takeIf { it.field == NotificationField.RUNNING_LONG_MINUTES }
    SettingRow("settings.runningLongMinutes", invalid) {
        TextSetting(SettingsTags.RUNNING_LONG_MINUTES, state.draft.runningLongMinutes) { text ->
            controller.edit { it.copy(runningLongMinutes = text) }
        }
    }
}

/** The global switches; the first takes the focus when the dialog opens. */
@Composable
private fun GlobalSwitches(
    draft: NotificationDraft,
    controller: NotificationSettingsController,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    ToggleSetting(SettingsTags.POPUPS, "settings.popups", draft.popups, Modifier.focusRequester(first)) { on ->
        controller.edit { it.copy(popups = on) }
    }
    ToggleSetting(SettingsTags.VOICE, "settings.voice", draft.voice) { on ->
        controller.edit { it.copy(voice = on) }
    }
}

/** The write failure, if any, and the Save and Cancel buttons. */
@Composable
private fun SettingsFooter(
    error: NotificationSettingsError?,
    controller: NotificationSettingsController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        error?.let {
            Label(SettingsStrings.of(it), Modifier.testTag(SettingsTags.SAVE_ERROR), SettingsColors.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            DialogButton(SettingsTags.SAVE, "settings.save", controller::save)
            DialogButton(SettingsTags.CANCEL, "settings.cancel", controller::cancel)
        }
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
