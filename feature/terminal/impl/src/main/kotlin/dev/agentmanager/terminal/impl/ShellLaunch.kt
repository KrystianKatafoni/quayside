package dev.agentmanager.terminal.impl

import java.nio.file.Path

private const val TERM_TYPE = "xterm-256color"
private val LOCALE_KEYS = listOf("LC_ALL", "LC_CTYPE", "LANG")

/** Pure argv and environment of a terminal process (AM-502 decisions 1 and 2). */
internal object ShellLaunch {
    /** Login shell: `[shell, -l]`. */
    fun shell(shell: Path): List<String> = listOf(shell.toString(), "-l")

    /** [argv] through the login shell: `[shell, -l, -c, exec "$@", agent-manager] + argv`. */
    fun command(
        shell: Path,
        argv: List<String>,
    ): List<String> = shell(shell) + listOf("-c", "exec \"\$@\"", "agent-manager") + argv

    /** `base + extra`, then `TERM=xterm-256color`; `LC_CTYPE=UTF-8` when no locale variable is set. */
    fun environment(
        base: Map<String, String>,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val merged = base + extra + ("TERM" to TERM_TYPE)
        return if (LOCALE_KEYS.any { it in merged }) merged else merged + ("LC_CTYPE" to "UTF-8")
    }
}
