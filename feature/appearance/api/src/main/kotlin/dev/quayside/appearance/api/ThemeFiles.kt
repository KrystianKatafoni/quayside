package dev.quayside.appearance.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path

/** theme.json files the user picks (FR-A6). Never throws; IO on its own dispatcher; never applies anything. */
public interface ThemeFiles {
    /** Reads [source] strictly: a present but invalid field is Err; a missing one is the default. */
    public suspend fun read(source: Path): Outcome<AppearanceSettings, ThemeFileError>

    /** Writes [settings] to [target] as theme.json v1, replacing an existing file atomically. */
    public suspend fun write(
        settings: AppearanceSettings,
        target: Path,
    ): Outcome<Unit, ThemeFileError>
}
