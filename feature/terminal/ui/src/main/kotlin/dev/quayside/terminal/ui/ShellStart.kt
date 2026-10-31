package dev.quayside.terminal.ui

import java.nio.file.Path

/**
 * Label and start directory of new shell tabs (AM-508, FR-T7); read at every [TerminalController.openShell], so a
 * changed setting reaches the next tab only. `app` implements it over the terminal settings. Main thread.
 */
public interface ShellStart {
    /** Label of a shell tab, e.g. `zsh`. */
    public val label: String

    /** Directory a new shell of [project] starts in. */
    public fun dirOf(project: TerminalProject): Path
}
