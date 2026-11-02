package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.SpeechSettings
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

    /** The accessibility label of a "restore default" button: `<restore>: <kind>`. */
    fun restoreLabel(kind: NotificationKind): String = "${get("settings.restore")}: ${of(kind)}"

    /** The name of [channel] in the matrix header. */
    fun of(channel: NotificationChannel): String = get("channel.${channel.name}")

    /** The accessibility label of a matrix checkbox: `<kind> – <channel>` (NFR-6). */
    fun cellLabel(
        kind: NotificationKind,
        channel: NotificationChannel,
    ): String = "${of(kind)} – ${of(channel)}"

    /** The label of an enum choice: `<prefix>.<NAME>`. */
    fun option(
        prefix: String,
        value: Enum<*>,
    ): String = get("$prefix.${value.name}")

    /** The message under the field the save refused. */
    fun of(field: InvalidField): String =
        when (field.field) {
            NotificationField.RUNNING_LONG_MINUTES -> {
                range(
                    "invalid.RUNNING_LONG_MINUTES",
                    NotificationSettings.MIN_RUNNING_LONG_MINUTES,
                    NotificationSettings.MAX_RUNNING_LONG_MINUTES,
                )
            }

            NotificationField.MAX_VISIBLE -> {
                range("invalid.MAX_VISIBLE", PopupSettings.MIN_VISIBLE, PopupSettings.MAX_VISIBLE_LIMIT)
            }

            NotificationField.RATE -> {
                range("invalid.RATE", SpeechSettings.MIN_RATE, SpeechSettings.MAX_RATE)
            }

            NotificationField.VOLUME -> {
                range("invalid.VOLUME", SpeechSettings.MIN_VOLUME, SpeechSettings.MAX_VOLUME)
            }

            NotificationField.TEMPLATE -> {
                get("invalid.TEMPLATE").replace("{max}", SpeechSettings.MAX_TEMPLATE_LENGTH.toString())
            }
        }

    private fun range(
        key: String,
        min: Int,
        max: Int,
    ): String = get(key).replace("{min}", min.toString()).replace("{max}", max.toString())

    /** The label of a voice choice: system voice, `<name> (<locale>)` or `<name> (niezainstalowany)`. */
    fun of(choice: VoiceChoice): String {
        val name = choice.id?.name ?: return get("voice.system")
        val locale = choice.locale
        return when {
            choice.missing -> get("voice.missing").replace("{name}", name)
            locale == null -> name
            else -> get("voice.installed").replace("{name}", name).replace("{locale}", locale)
        }
    }

    /** The message of a write that changed nothing. */
    fun of(error: NotificationSettingsError): String = get("error.${error.name}")
}
