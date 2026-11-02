package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/notification-settings.properties` (NFR-10), loaded without a system-locale fallback. */
internal object SettingsStrings {
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.notification-settings",
            Locale.forLanguageTag("pl"),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** The name of [kind] in the matrix. */
    fun of(kind: NotificationKind): String = get("kind.${kind.name}")

    /** The name of [channel] in the matrix header. */
    fun of(channel: NotificationChannel): String = get("channel.${channel.name}")

    /** The accessibility label of a matrix checkbox: `<kind> – <channel>` (NFR-6). */
    fun cellLabel(
        kind: NotificationKind,
        channel: NotificationChannel,
    ): String = "${of(kind)} – ${of(channel)}"

    /** The message under the field the save refused. */
    fun of(field: InvalidField): String =
        when (field.field) {
            NotificationField.RUNNING_LONG_MINUTES -> {
                get("invalid.RUNNING_LONG_MINUTES")
                    .replace("{min}", NotificationSettings.MIN_RUNNING_LONG_MINUTES.toString())
                    .replace("{max}", NotificationSettings.MAX_RUNNING_LONG_MINUTES.toString())
            }

            else -> {
                get("invalid.other")
            }
        }

    /** The message of a write that changed nothing. */
    fun of(error: NotificationSettingsError): String = get("error.${error.name}")
}
