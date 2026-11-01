package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationKind
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/popup.properties` (NFR-10), loaded without a system-locale fallback. */
internal object PopupStrings {
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.popup",
            Locale.forLanguageTag("pl"),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** The title of a popup about [kind]. */
    fun title(kind: NotificationKind): String = get("popup.kind.${kind.name}")
}
