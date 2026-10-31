package dev.quayside.missioncontrol.ui

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/missioncontrol.properties` (NFR-10), Polish, without a system-locale fallback. */
internal object MissionControlStrings {
    private val locale: Locale = Locale.forLanguageTag("pl")
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.missioncontrol",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    val keys: Set<String> get() = bundle.keySet()

    operator fun get(key: String): String = bundle.getString(key)

    /** [text] with its arguments filled in. */
    fun format(text: UiText): String = MessageFormat(get(text.key), locale).format(text.args.toTypedArray())

    /** The text of [key] with its one argument [arg], e.g. a counter. */
    fun formatted(
        key: String,
        arg: Any,
    ): String = format(UiText(key, listOf("$arg")))
}
