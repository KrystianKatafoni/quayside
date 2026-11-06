package dev.quayside.notifications.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/** Under the voice choice: the example of the voice, the failures and the hint about Polish voices. */
@Composable
internal fun VoiceNotes(state: NotificationDialogState) {
    val example =
        state.voices
            ?.firstOrNull { it.id == state.draft.speech.voice }
            ?.sample
            .orEmpty()
    if (example.isNotBlank()) {
        caption(
            SettingsTags.SPEECH_SAMPLE,
            SettingsStrings["voice.sample"].replace("{text}", example),
            mutedColor(),
        )
    }
    if (state.previewFailed) caption(SettingsTags.PREVIEW_FAILED, SettingsStrings["voice.previewFailed"], errColor())
    if (state.voicesFailed) caption(SettingsTags.VOICES_FAILED, SettingsStrings["voices.failed"], errColor())
    polishHint(state.voices)?.let { caption(SettingsTags.POLISH_HINT, hintText(it), mutedColor()) }
}

/** The accessibility label of the sample button: `<button text>: <voice>` (NFR-6). */
internal fun previewLabel(
    key: String,
    choice: VoiceChoice,
): String = "${SettingsStrings[key]}: ${choice.id?.name ?: SettingsStrings["voice.system"]}"

private fun hintText(hint: PolishHint): String =
    when (hint) {
        PolishHint.None -> SettingsStrings["hint.polishNone"]
        is PolishHint.Only -> SettingsStrings["hint.polishOnly"].replace("{name}", hint.name)
    }

@Composable
internal fun caption(
    tag: String,
    text: String,
    color: Color,
) {
    AmText(text, Modifier.testTag(tag), TextRole.CAPTION, color)
}
