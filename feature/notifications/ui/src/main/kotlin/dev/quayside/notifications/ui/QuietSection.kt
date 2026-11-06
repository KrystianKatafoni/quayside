package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText

/** The "Nie przeszkadzać" section (FR-N6): switch, times, Focus switch; always editable, even with the hours off. */
@Composable
internal fun QuietSection(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val quiet = state.draft.quiet
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        AmText(SettingsStrings["settings.quiet"])
        ToggleSetting(SettingsTags.QUIET_ENABLED, "settings.quietEnabled", quiet.enabled) { on ->
            controller.editQuiet { it.copy(enabled = on) }
        }
        TimeRow(
            "settings.quietFrom",
            SettingsTags.QUIET_FROM,
            quiet.from,
            state.invalidAt(NotificationField.QUIET_FROM),
        ) { text ->
            controller.editQuiet { it.copy(from = text) }
        }
        TimeRow(
            "settings.quietTo",
            SettingsTags.QUIET_TO,
            quiet.to,
            state.invalidAt(NotificationField.QUIET_TO),
        ) { text ->
            controller.editQuiet { it.copy(to = text) }
        }
        caption(SettingsTags.QUIET_HINT, SettingsStrings["settings.quietHint"], mutedColor())
        ToggleSetting(SettingsTags.RESPECT_FOCUS, "settings.respectFocus", quiet.respectFocus) { on ->
            controller.editQuiet { it.copy(respectFocus = on) }
        }
        caption(SettingsTags.FOCUS_NOTE, SettingsStrings["settings.focusNote"], mutedColor())
    }
}

private fun NotificationSettingsController.editQuiet(change: (QuietDraft) -> QuietDraft) {
    edit { it.copy(quiet = change(it.quiet)) }
}

@Composable
private fun TimeRow(
    labelKey: String,
    tag: String,
    value: String,
    invalid: InvalidField?,
    onChange: (String) -> Unit,
) {
    SettingRow(SettingsStrings[labelKey], invalid) { named -> TextSetting(tag, value, named, onChange) }
}
