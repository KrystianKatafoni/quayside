package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind

/** Semantics tags of the notification settings dialog for UI tests (AM-704). */
internal object SettingsTags {
    const val DIALOG = "notifications.settings"
    const val POPUPS = "notifications.settings.popups"
    const val VOICE = "notifications.settings.voice"
    const val RUNNING_LONG_MINUTES = "notifications.settings.runningLongMinutes"
    const val SAVE = "notifications.settings.save"
    const val CANCEL = "notifications.settings.cancel"

    /** The fixed message of a write that changed nothing. */
    const val SAVE_ERROR = "notifications.settings.saveError"

    /** The checkbox of [channel] for [kind] in the matrix. */
    fun cell(
        kind: NotificationKind,
        channel: NotificationChannel,
    ): String = "notifications.settings.matrix.${kind.name}.${channel.name}"

    /** The message under [field]; absent while the field is valid. */
    fun invalid(field: NotificationField): String = "notifications.settings.invalid.${field.name}"
}
