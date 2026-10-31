package dev.agentmanager.core.domain

import java.nio.file.Path

/**
 * A process to run: program and arguments without shell quoting, working directory and extra
 * environment. Execution belongs to `core:process`; this type only describes the command.
 */
public data class ShellCommand(
    val argv: List<String>,
    val workDir: Path,
    val env: Map<String, String> = emptyMap(),
) {
    init {
        require(argv.isNotEmpty()) { "ShellCommand needs at least the program name" }
        require(argv.first().isNotBlank()) { "ShellCommand program name must not be blank" }
    }

    /** Environment values may hold tokens, so they never appear in logs or error messages. */
    override fun toString(): String =
        "ShellCommand(argv=$argv, workDir=$workDir, env=${env.keys.sorted().associateWith { "***" }})"
}
