package dev.agentmanager.agentruntime.api

import dev.agentmanager.core.domain.SessionId

/**
 * World state a contract test starts from. [FakeAgentRuntime] takes it directly; the adapter test
 * (AM-203) turns it into the answers of a fake `claude` on `PATH`.
 *
 * @property sessions sessions the supervisor already knows about.
 * @property outputs `claude logs` output per session.
 * @property installed false = no `claude` binary at all.
 * @property version raw `claude --version` result when [installed].
 * @property agentViewEnabled whether Agent View (background sessions) is switched on.
 */
public data class RuntimeWorld(
    val sessions: List<RuntimeSession> = emptyList(),
    val outputs: Map<SessionId, String> = emptyMap(),
    val installed: Boolean = true,
    val version: String = RuntimeRequirements.MIN_CLAUDE_VERSION,
    val agentViewEnabled: Boolean = true,
)
