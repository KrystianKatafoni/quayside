package dev.quayside.terminal.impl

import dev.quayside.terminal.api.ShellProfile
import java.nio.file.Path

/** Which executable a [ShellProfile] means (ADR 0016, decision 4). Pure: [exists] says what is on the disk. */
internal object ShellProfiles {
    /**
     * `LOGIN_DEFAULT`: [loginShell]; `ZSH` `/bin/zsh`; `BASH` `/bin/bash`; `FISH` the first existing of
     * `/opt/homebrew/bin/fish`, `/usr/local/bin/fish`. Null when the executable does not exist.
     */
    fun resolve(
        profile: ShellProfile,
        loginShell: Path,
        exists: (Path) -> Boolean,
    ): Path? =
        when (profile) {
            ShellProfile.LOGIN_DEFAULT -> loginShell
            ShellProfile.ZSH -> Path.of("/bin/zsh").takeIf(exists)
            ShellProfile.BASH -> Path.of("/bin/bash").takeIf(exists)
            ShellProfile.FISH -> FISH_PATHS.firstOrNull(exists)
        }

    private val FISH_PATHS = listOf(Path.of("/opt/homebrew/bin/fish"), Path.of("/usr/local/bin/fish"))
}
