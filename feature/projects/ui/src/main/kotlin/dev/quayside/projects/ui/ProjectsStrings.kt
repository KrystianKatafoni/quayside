package dev.quayside.projects.ui

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/projects.properties` (NFR-10), loaded like `app.Strings`, without a system-locale fallback. */
internal object ProjectsStrings {
    private val locale: Locale = Locale.forLanguageTag("pl")
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.projects",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** [text] with its arguments filled in. */
    fun format(text: UiText): String = MessageFormat(get(text.key), locale).format(text.args.toTypedArray())
}
