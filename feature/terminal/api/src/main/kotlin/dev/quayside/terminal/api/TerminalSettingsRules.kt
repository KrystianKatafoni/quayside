package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path

/** Pure rules shared by impl, fakes and the dialog. */
public object TerminalSettingsRules {
    /** Allowed font sizes in points. */
    public val FONT_SIZES: IntRange = 8..32

    /** Allowed scrollback length in lines. */
    public val SCROLLBACK_LINES: IntRange = 1_000..100_000

    /** Longest accepted font family name, in characters, after trimming. */
    public const val MAX_FONT_FAMILY_LENGTH: Int = 80

    /** Trims [TerminalSettings.fontFamily] (blank becomes null), then checks the ranges; first bad field wins. */
    public fun validate(settings: TerminalSettings): Outcome<TerminalSettings, TerminalSettingsError.Invalid> {
        val family = settings.fontFamily?.trim()?.takeIf { it.isNotEmpty() }
        val bad =
            when {
                family != null && family.length > MAX_FONT_FAMILY_LENGTH -> TerminalSettingsField.FONT_FAMILY
                settings.fontSize !in FONT_SIZES -> TerminalSettingsField.FONT_SIZE
                settings.scrollbackLines !in SCROLLBACK_LINES -> TerminalSettingsField.SCROLLBACK_LINES
                else -> null
            }
        return if (bad == null) {
            Outcome.Ok(settings.copy(fontFamily = family))
        } else {
            Outcome.Err(TerminalSettingsError.Invalid(bad))
        }
    }

    /** [sessionDir] counts only for [StartDirectory.SESSION_WORKTREE]; null falls back to [projectDir]. */
    public fun startDirOf(
        choice: StartDirectory,
        projectDir: Path,
        sessionDir: Path?,
        home: Path,
    ): Path =
        when (choice) {
            StartDirectory.SESSION_WORKTREE -> sessionDir ?: projectDir
            StartDirectory.PROJECT -> projectDir
            StartDirectory.HOME -> home
        }

    /** Tab label: the file name of [loginShell] for LOGIN_DEFAULT, else `zsh`, `bash`, `fish`. */
    public fun shellLabelOf(
        profile: ShellProfile,
        loginShell: Path,
    ): String =
        when (profile) {
            ShellProfile.LOGIN_DEFAULT -> loginShell.fileName?.toString() ?: loginShell.toString()
            ShellProfile.ZSH -> "zsh"
            ShellProfile.BASH -> "bash"
            ShellProfile.FISH -> "fish"
        }
}
