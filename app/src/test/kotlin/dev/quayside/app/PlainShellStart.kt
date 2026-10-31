package dev.quayside.app

import dev.quayside.terminal.ui.ShellStart
import dev.quayside.terminal.ui.TerminalProject
import java.nio.file.Path

/** [ShellStart] of tests that do not care about terminal settings: label `zsh`, shells start in the project dir. */
internal class PlainShellStart(
    override val label: String = "zsh",
) : ShellStart {
    override fun dirOf(project: TerminalProject): Path = project.dir
}
