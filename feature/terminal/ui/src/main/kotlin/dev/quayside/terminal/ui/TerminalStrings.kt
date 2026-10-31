package dev.quayside.terminal.ui

import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import dev.quayside.terminal.api.TerminalSettingsRules
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

    /** The message under the field the settings dialog refused. */
    fun of(field: TerminalSettingsField): String =
        when (field) {
            TerminalSettingsField.FONT_FAMILY -> {
                format("settings.invalid.FONT_FAMILY", TerminalSettingsRules.MAX_FONT_FAMILY_LENGTH)
            }

            TerminalSettingsField.FONT_SIZE -> {
                format(
                    "settings.invalid.FONT_SIZE",
                    TerminalSettingsRules.FONT_SIZES.first,
                    TerminalSettingsRules.FONT_SIZES.last,
                )
            }

            TerminalSettingsField.SCROLLBACK_LINES -> {
                format(
                    "settings.invalid.SCROLLBACK_LINES",
                    TerminalSettingsRules.SCROLLBACK_LINES.first,
                    TerminalSettingsRules.SCROLLBACK_LINES.last,
                )
            }
        }

    /** The notice of the settings dialog about `terminal.json`. */
    fun of(issue: TerminalSettingsIssue): String = get("settings.notice.${issue.name}")

    /** The name of [profile] in the settings dialog. */
    fun of(profile: ShellProfile): String = get("settings.shell.${profile.name}")

    /** The name of [directory] in the settings dialog. */
    fun of(directory: StartDirectory): String = get("settings.startDirectory.${directory.name}")
}
