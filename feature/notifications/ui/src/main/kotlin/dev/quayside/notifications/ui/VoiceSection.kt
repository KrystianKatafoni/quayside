package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.notifications.api.NotificationKind

/** The "Głos" section (FR-N4): voice, rate, volume, the message of every kind and three switches. */
@Composable
internal fun VoiceSection(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val speech = state.draft.speech
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        Label(SettingsStrings["settings.speech"])
        VoiceRow(state, controller)
        NumberRow("settings.rate", SettingsTags.RATE, speech.rate, state.invalidAt(NotificationField.RATE)) { text ->
            controller.editSpeech { it.copy(rate = text) }
        }
        NumberRow(
            "settings.volume",
            SettingsTags.VOLUME,
            speech.volume,
            state.invalidAt(NotificationField.VOLUME),
        ) { text ->
            controller.editSpeech { it.copy(volume = text) }
        }
        Label(SettingsStrings["settings.templates"])
        NotificationKind.entries.forEach { TemplateRow(it, state, controller) }
        SpeechSwitches(speech, controller)
    }
}

private fun NotificationDialogState.invalidAt(field: NotificationField): InvalidField? =
    invalid?.takeIf { it.field == field }

private fun NotificationSettingsController.editSpeech(change: (SpeechDraft) -> SpeechDraft) {
    edit { it.copy(speech = change(it.speech)) }
}

/** The voice choice; the saved voice stays in the list while the user steps through it. */
@Composable
private fun VoiceRow(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val saved = remember { state.draft.speech.voice }
    val choices = voiceChoices(state.voices, saved)
    val selected = choices.indexOfFirst { it.id == state.draft.speech.voice }.coerceAtLeast(minimumValue = 0)
    val select = { index: Int -> controller.editSpeech { it.copy(voice = choices[index].id) } }
    SettingRow(SettingsStrings["settings.speechVoice"], invalid = null) { named ->
        val names = choices.map { it.id?.name }
        ChoiceField(
            SettingsTags.SPEECH_VOICE,
            choices.map(SettingsStrings::of),
            selected,
            named.letterJump(names, selected, select),
            select,
        )
    }
    if (state.voicesFailed) {
        val tag = Modifier.testTag(SettingsTags.VOICES_FAILED)
        Label(SettingsStrings["voices.failed"], tag, SettingsColors.error, size = 12)
    }
}

/** A letter key selects the next voice (from the current one, wrapping) whose name starts with it. */
private fun Modifier.letterJump(
    names: List<String?>,
    selected: Int,
    onSelect: (Int) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val target = event.letter()?.let { jumpTarget(names, selected, it) }
        target?.let(onSelect)
        target != null
    }

private fun KeyEvent.letter(): Char? {
    val plain = type == KeyEventType.KeyDown && !isMetaPressed && !isCtrlPressed && !isAltPressed
    return key.nativeKeyCode.takeIf { plain && it in 'A'.code..'Z'.code }?.toChar()
}

@Composable
private fun NumberRow(
    labelKey: String,
    tag: String,
    value: String,
    invalid: InvalidField?,
    onChange: (String) -> Unit,
) {
    SettingRow(SettingsStrings[labelKey], invalid) { named -> TextSetting(tag, value, named, onChange) }
}

@Composable
private fun TemplateRow(
    kind: NotificationKind,
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val invalid = state.invalidAt(NotificationField.TEMPLATE)?.takeIf { it.kind == kind }
    SettingRow(SettingsStrings.of(kind), invalid) { named ->
        TextSetting(
            SettingsTags.template(kind),
            state.draft.speech.templates
                .getValue(kind),
            named,
        ) { text ->
            controller.editSpeech { it.copy(templates = it.templates + (kind to text)) }
        }
        DialogButton(
            SettingsTags.restore(kind),
            "settings.restore",
            SettingsStrings.restoreLabel(kind),
        ) { controller.restoreTemplate(kind) }
    }
}

@Composable
private fun SpeechSwitches(
    speech: SpeechDraft,
    controller: NotificationSettingsController,
) {
    ToggleSetting(SettingsTags.READ_QUESTION, "settings.readQuestion", speech.readQuestion) { on ->
        controller.editSpeech { it.copy(readQuestion = on) }
    }
    ToggleSetting(SettingsTags.ONLY_IN_BACKGROUND, "settings.onlyInBackground", speech.onlyInBackground) { on ->
        controller.editSpeech { it.copy(onlyInBackground = on) }
    }
    ToggleSetting(
        SettingsTags.MUTE_MICROPHONE,
        "settings.muteWhenMicrophoneActive",
        speech.muteWhenMicrophoneActive,
    ) { on ->
        controller.editSpeech { it.copy(muteWhenMicrophoneActive = on) }
    }
    val tag = Modifier.testTag(SettingsTags.MIC_NOTE)
    Label(SettingsStrings["settings.micNote"], tag, SettingsColors.secondary, size = 12)
}
