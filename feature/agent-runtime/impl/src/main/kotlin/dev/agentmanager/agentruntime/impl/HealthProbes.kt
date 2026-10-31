package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.RuntimeRequirements

/** Dotted numeric Claude Code version `major.minor.patch`; [toString] gives `2.1.302`. */
internal data class ClaudeVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ClaudeVersion> {
    /** Numeric, part by part. */
    override fun compareTo(other: ClaudeVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    internal companion object {
        private const val PARTS = 3

        // A suffix such as ` (Claude Code)`, `-beta.1` or `+build` is ignored; a fourth part is not.
        private val VERSION = Regex("""^(\d+)\.(\d+)\.(\d+)(?![\d.])""")

        /** Version at the start of the first non-blank line of `claude --version` stdout; null when there is none. */
        fun parse(stdout: String): ClaudeVersion? {
            val line =
                stdout
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty()
            // An overflowing part gives null and drops out, so the version is complete or none.
            val parts =
                VERSION
                    .find(line)
                    ?.groupValues
                    ?.drop(1)
                    ?.mapNotNull { it.toIntOrNull() }
            return if (parts?.size == PARTS) ClaudeVersion(parts[0], parts[1], parts[2]) else null
        }

        /** [RuntimeRequirements.MIN_CLAUDE_VERSION] parsed; a unit test guards that it parses. */
        val MINIMUM: ClaudeVersion =
            requireNotNull(parse(RuntimeRequirements.MIN_CLAUDE_VERSION)) { "MIN_CLAUDE_VERSION must parse" }
    }
}

/** Reads the answer of `claude daemon status`. */
internal object DaemonStatus {
    private val PID = Regex("""^pid:\s*\d+$""")

    /** True only for exit code 0 and a `pid: <digits>` line; any other answer means "not running". */
    fun isRunning(
        exitCode: Int,
        stdout: String,
    ): Boolean = exitCode == 0 && stdout.lineSequence().any { PID.matches(it.trim()) }
}

/** Recognises the answer of `claude agents --json` when Agent View is switched off. */
internal object AgentViewProbe {
    private val DISABLED = Regex("""is disabled by (?:the )?(?:CLAUDE_CODE_DISABLE_AGENT_VIEW|'disableAgentView')""")

    /** True when stderr or stdout says `… is disabled by` CLAUDE_CODE_DISABLE_AGENT_VIEW or 'disableAgentView'. */
    fun isDisabledMessage(
        stderr: String,
        stdout: String,
    ): Boolean = listOf(stderr, stdout).any { DISABLED.containsMatchIn(TerminalText.clean(it)) }
}
