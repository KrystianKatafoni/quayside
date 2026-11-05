package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText

/** The "Popup" section (FR-N3): position, duration, max visible, style; always editable, even with popups off. */
@Composable
internal fun PopupSection(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val popup = state.draft.popup
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        AmText(SettingsStrings["settings.popup"])
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
    SettingRow(SettingsStrings["settings.maxVisible"], invalid) { named ->
        TextSetting(SettingsTags.MAX_VISIBLE, state.draft.popup.maxVisible, named) { text ->
            controller.edit { it.copy(popup = it.popup.copy(maxVisible = text)) }
        }
    }
}

/** A [SettingRow] (`settings.popup<Prefix>`) with a [Dropdown] over every [T]; labels from `<prefix>.<NAME>`. */
@Composable
private inline fun <reified T : Enum<T>> EnumRow(
    prefix: String,
    tag: String,
    selected: T,
    noinline onSelect: (T) -> Unit,
) {
    val entries = enumValues<T>()
    val label = SettingsStrings["settings.popup${prefix.replaceFirstChar { it.uppercase() }}"]
    SettingRow(label, invalid = null) { _ ->
        val items = AmDropdownItems(entries.map { SettingsStrings.option(prefix, it) }, selected.ordinal)
        Dropdown(tag, label, items) { index -> onSelect(entries[index]) }
    }
}
