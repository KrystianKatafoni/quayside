package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettingsField

private val panelShape = RoundedCornerShape(size = 10.dp)

/**
 * The "Ustawienia terminala" dialog over the whole window (AM-508, FR-T7); draws nothing while [controller] has
 * it closed. A focusable popup covering the whole window, so keys reach its controls and not the window behind
 * it; Escape is "Cancel".
 */
@Composable
public fun TerminalSettingsDialog(controller: TerminalSettingsController) {
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
    state: SettingsDialogState,
    controller: TerminalSettingsController,
) {
    Column(
        modifier =
            Modifier
                .width(width = 640.dp)
                .background(color = TerminalColors.panel, shape = panelShape)
                .padding(all = 16.dp)
                .testTag(TerminalTags.SETTINGS_DIALOG)
                .onPreviewKeyEvent { event ->
                    val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (escape) controller.cancel()
                    escape
                },
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Label(TerminalStrings["settings.title"])
        state.notice?.let { Notice(TerminalStrings.of(it)) }
        Label(TerminalStrings["settings.hint"], color = TerminalColors.secondary)
        ChoiceRows(state, controller)
        FieldRows(state, controller)
        ToggleRows(state.draft, controller)
        SettingsFooter(state.storageFailed, controller)
    }
}

/** The storage failure, if any, and the Save and Cancel buttons. */
@Composable
private fun SettingsFooter(
    storageFailed: Boolean,
    controller: TerminalSettingsController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        if (storageFailed) {
            Label(
                TerminalStrings["settings.storageError"],
                Modifier.testTag(TerminalTags.SETTINGS_STORAGE_ERROR),
                TerminalColors.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            DialogButton(TerminalTags.SETTINGS_SAVE, "settings.save", controller::save)
            DialogButton(TerminalTags.SETTINGS_CANCEL, "settings.cancel", controller::cancel)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Label(text, Modifier.testTag(TerminalTags.SETTINGS_NOTICE), TerminalColors.warning)
}

/** Shell and start directory; the first control takes the focus when the dialog opens. */
@Composable
private fun ChoiceRows(
    state: SettingsDialogState,
    controller: TerminalSettingsController,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val shells = ShellProfile.entries
    SettingRow("settings.shell", invalid = null) {
        ChoiceField(
            TerminalTags.SETTINGS_SHELL,
            shells.map(TerminalStrings::of),
            shells.indexOf(state.draft.shell),
            Modifier.focusRequester(first),
        ) { index -> controller.edit { it.copy(shell = shells[index]) } }
    }
    val directories = StartDirectory.entries
    SettingRow("settings.startDirectory", invalid = null) {
        ChoiceField(
            TerminalTags.SETTINGS_START_DIRECTORY,
            directories.map(TerminalStrings::of),
            directories.indexOf(state.draft.startDirectory),
        ) { index -> controller.edit { it.copy(startDirectory = directories[index]) } }
    }
}

/** Font family, font size and scrollback as text; a refused field shows its message under it. */
@Composable
private fun FieldRows(
    state: SettingsDialogState,
    controller: TerminalSettingsController,
) {
    val draft = state.draft
    SettingRow("settings.fontFamily", state.invalidAt(TerminalSettingsField.FONT_FAMILY)) {
        TextSetting(TerminalTags.SETTINGS_FONT_FAMILY, draft.fontFamily) { text ->
            controller.edit { it.copy(fontFamily = text) }
        }
    }
    SettingRow("settings.fontSize", state.invalidAt(TerminalSettingsField.FONT_SIZE)) {
        TextSetting(TerminalTags.SETTINGS_FONT_SIZE, draft.fontSize) { text ->
            controller.edit { it.copy(fontSize = text) }
        }
    }
    SettingRow("settings.scrollback", state.invalidAt(TerminalSettingsField.SCROLLBACK_LINES)) {
        TextSetting(TerminalTags.SETTINGS_SCROLLBACK, draft.scrollbackLines) { text ->
            controller.edit { it.copy(scrollbackLines = text) }
        }
    }
}

@Composable
private fun ToggleRows(
    draft: SettingsDraft,
    controller: TerminalSettingsController,
) {
    ToggleSetting(TerminalTags.SETTINGS_LIGATURES, "settings.ligatures", draft.ligatures) { on ->
        controller.edit { it.copy(ligatures = on) }
    }
    ToggleSetting(TerminalTags.SETTINGS_OPTION_AS_META, "settings.optionAsMeta", draft.optionAsMeta) { on ->
        controller.edit { it.copy(optionAsMeta = on) }
    }
}

private fun SettingsDialogState.invalidAt(field: TerminalSettingsField): TerminalSettingsField? =
    invalid.takeIf { it == field }

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
