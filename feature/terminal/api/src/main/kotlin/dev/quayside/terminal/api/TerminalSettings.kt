package dev.quayside.terminal.api

/** Shell of new shell tabs (FR-T7). [LOGIN_DEFAULT] is the user's login shell (`$SHELL`). */
public enum class ShellProfile { LOGIN_DEFAULT, ZSH, BASH, FISH }

/** Where a new shell tab starts (FR-T7, docs/06). */
public enum class StartDirectory { SESSION_WORKTREE, PROJECT, HOME }

/**
 * Terminal settings (docs/06 defaults). Apply to tabs opened later, never to open ones (ADR 0016). Immutable.
 *
 * @property fontFamily null: the terminal's default monospace font.
 * @property optionAsMeta Option sends Escape (Meta); also blocks Option characters such as `ą`.
 */
public data class TerminalSettings(
    val shell: ShellProfile = ShellProfile.LOGIN_DEFAULT,
    val startDirectory: StartDirectory = StartDirectory.SESSION_WORKTREE,
    val fontFamily: String? = null,
    val fontSize: Int = 13,
    val scrollbackLines: Int = 100_000,
    val ligatures: Boolean = true,
    val optionAsMeta: Boolean = false,
)

/** The settings field that failed validation. */
public enum class TerminalSettingsField { FONT_FAMILY, FONT_SIZE, SCROLLBACK_LINES }

/** Why [TerminalSettingsRepo.update] saved nothing; the current settings stay. */
public sealed interface TerminalSettingsError {
    /** [field] is outside its allowed values. */
    public data class Invalid(
        val field: TerminalSettingsField,
    ) : TerminalSettingsError

    /** `terminal.json` could not be written, or was written by a newer app (never overwritten). */
    public data object StorageFailed : TerminalSettingsError
}

/** What happened to `terminal.json` at start; null in [TerminalSettingsRepo.issue] when nothing. */
public enum class TerminalSettingsIssue { RECOVERED_FROM_BROKEN_FILE, READ_FAILED, NEWER_SCHEMA }
