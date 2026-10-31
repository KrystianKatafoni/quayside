package dev.quayside.app

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/strings.properties` (NFR-10: Polish now, ready for de/en bundles). */
internal object Strings {
    // No fallback to the JVM default locale: an English system must still get the requested Polish texts.
    private val locale: Locale = Locale.forLanguageTag("pl")
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.strings",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** The text of [key] with [args] filled in (`{0}`, `{1}`, …). */
    fun format(
        key: String,
        vararg args: Any,
    ): String = MessageFormat(get(key), locale).format(args)
}
