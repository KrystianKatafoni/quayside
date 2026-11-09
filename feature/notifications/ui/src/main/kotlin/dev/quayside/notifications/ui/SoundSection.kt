package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundSettings

/** The "Dźwięk" section (FR-N1): the volume and, for every kind, the sound with a preview button. */
@Composable
internal fun SoundSection(
    state: NotificationDialogState,
    sounds: SoundPreviewController,
    controller: NotificationSettingsController,
) {
    DisposableEffect(sounds) {
        sounds.load()
        onDispose { sounds.stop() }
    }
    val panel by sounds.state.collectAsState()
    val draft = state.draft.sound
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        AmText(SettingsStrings["settings.sound"])
        AmText(SettingsStrings["settings.soundHint"], role = TextRole.CAPTION, color = mutedColor())
        SettingRow(SettingsStrings["settings.soundVolume"], state.invalidAt(NotificationField.SOUND_VOLUME)) { named ->
            TextSetting(SettingsTags.SOUND_VOLUME, draft.volume, named) { text ->
                controller.edit { it.copy(sound = it.sound.copy(volume = text)) }
            }
        }
        NotificationKind.entries.forEach { SoundRow(it, state, panel, sounds, controller) }
        SoundNotes(panel)
    }
}

@Composable
private fun SoundNotes(panel: SoundPanelState) {
    if (panel.listFailed) {
        AmText(
            SettingsStrings["sound.listFailed"],
            Modifier.testTag(SettingsTags.SOUNDS_FAILED),
            TextRole.CAPTION,
            errColor(),
        )
    }
    if (panel.previewFailed) {
        AmText(
            SettingsStrings["sound.previewFailed"],
            Modifier.testTag(SettingsTags.SOUND_PREVIEW_FAILED),
            TextRole.CAPTION,
            errColor(),
        )
    }
}

@Composable
private fun SoundRow(
    kind: NotificationKind,
    state: NotificationDialogState,
    panel: SoundPanelState,
    sounds: SoundPreviewController,
    controller: NotificationSettingsController,
) {
    val current =
        state.draft.sound.sounds
            .getValue(kind)
    // The saved sound stays in the list while the user steps through it.
    val saved = remember { current }
    val choices = soundChoices(panel.sounds, saved)
    val selected = choices.indexOf(current).coerceAtLeast(minimumValue = 0)
    val label = SettingsStrings.of(kind)
    SettingRow(label, invalid = null) { _ ->
        val items = AmDropdownItems(choices.map { it.name }, selected)
        Dropdown(SettingsTags.sound(kind), label, items) { index ->
            controller.edit { it.copy(sound = it.sound.copy(sounds = it.sound.sounds + (kind to choices[index]))) }
        }
        val key = if (panel.previewing == kind) "sound.previewStop" else "sound.preview"
        DialogButton(SettingsTags.soundPreview(kind), key, "${SettingsStrings[key]}: $label") {
            val volume = parseInRange(state.draft.sound.volume, SoundSettings.MIN_VOLUME..SoundSettings.MAX_VOLUME)
            sounds.toggle(kind, choices[selected], volume ?: SoundSettings.DEFAULT_VOLUME)
        }
    }
}

/** The installed sounds, with [saved] first when it is not among them; just [saved] while [installed] is null. */
internal fun soundChoices(
    installed: List<SoundId>?,
    saved: SoundId,
): List<SoundId> =
    if (installed == null ||
        saved in installed
    ) {
        installed ?: listOf(saved)
    } else {
        listOf(saved) + installed
    }
