package dev.quayside.summaries.ui

import dev.quayside.summaries.api.SummarySettingsError
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/summary-settings.properties` (NFR-10), loaded without a system-locale fallback. */
internal object SummaryStrings {
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.summary-settings",
            Locale.forLanguageTag("pl"),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** The message of a write that changed nothing. */
    fun of(error: SummarySettingsError): String = get("error.${error.name}")
}
