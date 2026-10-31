package dev.quayside.agentruntime.api

/** What Quayside requires from the local Claude Code installation (NFR-9). */
public object RuntimeRequirements {
    /**
     * Oldest Claude Code version with Agent View (docs/compat.md). [AgentRuntime.health] reports an
     * older version as [RuntimeError.UnsupportedVersion] with this value as `required`.
     */
    public const val MIN_CLAUDE_VERSION: String = "2.1.139"
}
