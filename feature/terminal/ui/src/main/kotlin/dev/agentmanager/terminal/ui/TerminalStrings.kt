package dev.agentmanager.terminal.ui

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** UI texts from `i18n/terminal.properties` (NFR-10), loaded without a system-locale fallback. */
internal object TerminalStrings {
    private val locale: Locale = Locale.forLanguageTag("pl")
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle(
            "i18n.terminal",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    operator fun get(key: String): String = bundle.getString(key)

    /** The text of [key] with [args] filled in (`{0}`, `{1}`, …). */
    fun format(
        key: String,
        vararg args: Any,
    ): String = MessageFormat(get(key), locale).format(args)

    /** The message of the error bar for [failure]. */
    fun of(failure: OpenFailure): String =
        when (failure) {
            OpenFailure.WORK_DIR_MISSING -> get("error.workDirMissing")
            OpenFailure.START_FAILED -> get("error.startFailed")
        }
}
