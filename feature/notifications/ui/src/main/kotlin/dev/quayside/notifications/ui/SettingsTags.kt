package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind

/** Semantics tags of the notification settings dialog for UI tests (AM-704). */
internal object SettingsTags {
    const val DIALOG = "notifications.settings"
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
    const val VOICES_FAILED = "notifications.settings.speech.voicesFailed"
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

    /** The message under [field]; absent while the field is valid. */
    fun invalid(field: NotificationField): String = "notifications.settings.invalid.${field.name}"
}
