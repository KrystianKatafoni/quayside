package dev.quayside.appearance.api

/** Why a theme file failed; fixed categories, never file contents or paths in texts (ENG-C8). */
public sealed interface ThemeFileError {
    /** Missing, no permission, or larger than 64 KiB. */
    public data object ReadFailed : ThemeFileError

    /** Not UTF-8 JSON object, or no `schemaVersion` or `data` object. */
    public data object NotThemeFile : ThemeFileError

    /** Written by a newer app version. */
    public data class NewerVersion(
        val found: Int,
    ) : ThemeFileError

    /** A field is present but invalid (unknown id, bad enum, wrong type). */
    public data object InvalidValue : ThemeFileError

    /** The file could not be written. */
    public data object WriteFailed : ThemeFileError
}
