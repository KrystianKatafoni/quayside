package dev.quayside.appearance.ui

import dev.quayside.appearance.api.ThemeMode
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/appearance.properties` (NFR-10), loaded without a system-locale fallback. */
internal object AppearanceStrings {
    private val locale: Locale = Locale.forLanguageTag("pl")
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.appearance",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    operator fun get(key: String): String = bundle.getString(key)

    /** The text of [key] with [args] filled in (`{0}`, `{1}`, …). */
    fun format(
        key: String,
        vararg args: Any,
    ): String = MessageFormat(get(key), locale).format(args)

    /** The badge of a theme: light or dark, normal or high contrast. */
    fun of(mode: ThemeMode): String = get("mode.${mode.name}")

    /** The fixed message of an apply that failed. */
    fun of(error: ApplyError): String = get("error.${error.name}")

    /** The fixed message after a finished import or export. */
    fun of(notice: FileNotice): String = get("notice.${notice.name}")
}
