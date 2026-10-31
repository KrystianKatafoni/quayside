package dev.agentmanager.commandpalette.ui

import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/palette.properties` (NFR-10), loaded without a system-locale fallback. */
internal object PaletteStrings {
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.palette",
            Locale.forLanguageTag("pl"),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)
}
