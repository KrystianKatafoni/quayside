package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** The "Popup" section (FR-N3): position, duration, max visible, style; always editable, even with popups off. */
@Composable
internal fun PopupSection(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val popup = state.draft.popup
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        Label(SettingsStrings["settings.popup"])
        EnumRow("position", SettingsTags.POPUP_POSITION, popup.position) { value ->
            controller.edit { it.copy(popup = it.popup.copy(position = value)) }
        }
        EnumRow("duration", SettingsTags.POPUP_DURATION, popup.duration) { value ->
            controller.edit { it.copy(popup = it.popup.copy(duration = value)) }
        }
        MaxVisibleRow(state, controller)
        EnumRow("style", SettingsTags.POPUP_STYLE, popup.style) { value ->
            controller.edit { it.copy(popup = it.popup.copy(style = value)) }
        }
    }
}

@Composable
private fun MaxVisibleRow(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val invalid = state.invalid?.takeIf { it.field == NotificationField.MAX_VISIBLE }
    SettingRow("settings.maxVisible", invalid) {
        TextSetting(SettingsTags.MAX_VISIBLE, state.draft.popup.maxVisible) { text ->
            controller.edit { it.copy(popup = it.popup.copy(maxVisible = text)) }
        }
    }
}

/** A [SettingRow] (`settings.popup<Prefix>`) with a [ChoiceField] over every [T]; labels from `<prefix>.<NAME>`. */
@Composable
private inline fun <reified T : Enum<T>> EnumRow(
    prefix: String,
    tag: String,
    selected: T,
    noinline onSelect: (T) -> Unit,
) {
    val entries = enumValues<T>()
    SettingRow("settings.popup${prefix.replaceFirstChar { it.uppercase() }}", invalid = null) {
        ChoiceField(tag, entries.map { SettingsStrings.option(prefix, it) }, selected.ordinal) { index ->
            onSelect(entries[index])
        }
    }
}
