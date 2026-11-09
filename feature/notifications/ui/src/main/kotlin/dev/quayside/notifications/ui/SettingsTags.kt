package dev.quayside.notifications.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind

/** Semantics tags of the notification settings dialog for UI tests (AM-704). */
internal object SettingsTags {
    const val DIALOG = "notifications.settings"
    const val VOICE_CHIP = "notifications.voiceChip"
    const val POPUPS = "notifications.settings.popups"
    const val VOICE = "notifications.settings.voice"
    const val RUNNING_LONG_MINUTES = "notifications.settings.runningLongMinutes"
    const val POPUP_POSITION = "notifications.settings.popup.position"
    const val POPUP_DURATION = "notifications.settings.popup.duration"
    const val MAX_VISIBLE = "notifications.settings.popup.maxVisible"
    const val POPUP_STYLE = "notifications.settings.popup.style"
    const val SPEECH_VOICE = "notifications.settings.speech.voice"
    const val RATE = "notifications.settings.speech.rate"
    const val VOLUME = "notifications.settings.speech.volume"
    const val READ_QUESTION = "notifications.settings.speech.readQuestion"
    const val ONLY_IN_BACKGROUND = "notifications.settings.speech.onlyInBackground"
    const val MUTE_MICROPHONE = "notifications.settings.speech.muteMicrophone"
    const val MIC_NOTE = "notifications.settings.speech.micNote"
    const val PREVIEW = "notifications.settings.speech.preview"
    const val SPEECH_SAMPLE = "notifications.settings.speech.sample"
    const val PREVIEW_FAILED = "notifications.settings.speech.previewFailed"
    const val POLISH_HINT = "notifications.settings.speech.polishHint"
    const val VOICES_FAILED = "notifications.settings.speech.voicesFailed"
    const val SOUND_VOLUME = "notifications.settings.sound.volume"
    const val SOUNDS_FAILED = "notifications.settings.sound.listFailed"
    const val SOUND_PREVIEW_FAILED = "notifications.settings.sound.previewFailed"
    const val QUIET_ENABLED = "notifications.settings.quiet.enabled"
    const val QUIET_FROM = "notifications.settings.quiet.from"
    const val QUIET_TO = "notifications.settings.quiet.to"
    const val QUIET_HINT = "notifications.settings.quiet.hint"
    const val RESPECT_FOCUS = "notifications.settings.quiet.respectFocus"
    const val FOCUS_NOTE = "notifications.settings.quiet.focusNote"
    const val PROJECTS_HINT = "notifications.settings.projects.hint"
    const val PROJECTS_EMPTY = "notifications.settings.projects.empty"
    const val SAVE = "notifications.settings.save"
    const val CANCEL = "notifications.settings.cancel"

    /** The fixed message of a write that changed nothing. */
    const val SAVE_ERROR = "notifications.settings.saveError"

    /** The checkbox of [channel] for [kind] in the matrix. */
    fun cell(
        kind: NotificationKind,
        channel: NotificationChannel,
    ): String = "notifications.settings.matrix.${kind.name}.${channel.name}"

    /** The template field of [kind]. */
    fun template(kind: NotificationKind): String = "notifications.settings.speech.template.${kind.name}"

    /** The "restore default" button of the template of [kind]. */
    fun restore(kind: NotificationKind): String = "notifications.settings.speech.restore.${kind.name}"

    /** The sound choice of [kind]. */
    fun sound(kind: NotificationKind): String = "notifications.settings.sound.choice.${kind.name}"

    /** The preview button of the sound of [kind]. */
    fun soundPreview(kind: NotificationKind): String = "notifications.settings.sound.preview.${kind.name}"

    /** The message under [field]; absent while the field is valid. */
    fun invalid(field: NotificationField): String = "notifications.settings.invalid.${field.name}"

    /** The profile choice of the project [id]. */
    fun projectProfile(id: ProjectId): String = "notifications.settings.projects.profile.${id.value}"

    /** The voice choice of the project [id]. */
    fun projectVoice(id: ProjectId): String = "notifications.settings.projects.voice.${id.value}"
}
